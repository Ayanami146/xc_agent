"""维修手册增量同步和稠密检索服务测试。"""

from pathlib import Path
from typing import Any

import pytest
from langchain_core.documents import Document
from pydantic import SecretStr

from agent_service.config import Settings
from agent_service.core.exceptions import RagUnavailableError
from agent_service.schemas.rag import RagManual
from agent_service.services import manual_rag as manual_rag_module
from agent_service.services.manual_rag import ManualRagService


class MemoryVectorStore:
    """实现测试所需最小 Chroma 接口，并记录增删操作。"""

    def __init__(self) -> None:
        self.documents: dict[str, Document] = {}
        self.add_calls = 0
        self.deleted_ids: list[str] = []
        self.search_results: list[tuple[Document, float]] = []
        self.last_k = 0
        self.fail_add = False

    def get(self, *, include: list[str]) -> dict[str, Any]:
        del include
        return {
            "ids": list(self.documents),
            "metadatas": [document.metadata for document in self.documents.values()],
        }

    def delete(self, *, ids: list[str]) -> None:
        self.deleted_ids.extend(ids)
        for chunk_id in ids:
            self.documents.pop(chunk_id, None)

    def add_documents(self, documents: list[Document], *, ids: list[str]) -> None:
        if self.fail_add:
            raise RuntimeError("embedding unavailable")
        self.add_calls += 1
        self.documents.update(zip(ids, documents, strict=True))

    def similarity_search_with_relevance_scores(
        self,
        query: str,
        *,
        k: int,
    ) -> list[tuple[Document, float]]:
        del query
        self.last_k = k
        return self.search_results


class ManifestRagService(ManualRagService):
    def __init__(self, settings: Settings, vector_store: MemoryVectorStore) -> None:
        super().__init__(settings, vector_store)  # type: ignore[arg-type]
        self.manifest: list[RagManual] = []

    async def _fetch_manifest(self) -> list[RagManual]:
        return self.manifest


def _settings(directory: Path) -> Settings:
    return Settings(
        _env_file=None,
        environment="test",
        model_provider="mock",
        context_storage_enabled=False,
        manual_storage_directory=directory,
        rag_score_threshold=0.35,
        rag_top_k=5,
    )


def _manual(*, version: int = 1) -> RagManual:
    return RagManual(
        sourceId=8,
        documentId="man_txt",
        title="文本手册",
        summary="测试",
        objectKey="0123456789abcdef0123456789abcdef.txt",
        fileName="manual.txt",
        contentType="text/plain",
        sha256="a" * 64,
        versionNo=version,
        resourceVersion=version,
    )


def test_factory_sends_text_instead_of_openai_token_ids(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """阿里云兼容接口只接受字符串，禁止 LangChain 预先提交 token 整数数组。"""

    captured: dict[str, Any] = {}

    class FakeEmbeddings:
        def __init__(self, **kwargs: Any) -> None:
            captured["embedding"] = kwargs

    class FakeChroma:
        def __init__(self, **kwargs: Any) -> None:
            captured["chroma"] = kwargs

    monkeypatch.setattr(manual_rag_module, "OpenAIEmbeddings", FakeEmbeddings)
    monkeypatch.setattr(manual_rag_module, "Chroma", FakeChroma)
    settings = _settings(tmp_path).model_copy(
        update={
            "embedding_api_key": SecretStr("test-key"),
            "chroma_directory": tmp_path / "chroma",
        }
    )

    manual_rag_module.create_manual_rag_service(settings)

    embedding_options = captured["embedding"]
    assert embedding_options["check_embedding_ctx_length"] is False


@pytest.mark.asyncio
async def test_sync_adds_once_rebuilds_changes_and_removes_archived(tmp_path: Path) -> None:
    manual_path = tmp_path / "0123456789abcdef0123456789abcdef.txt"
    manual_path.write_text("打印机驱动安装前请确认设备型号。", encoding="utf-8")
    store = MemoryVectorStore()
    service = ManifestRagService(_settings(tmp_path), store)

    service.manifest = [_manual()]
    await service.sync_index()
    first_ids = list(store.documents)
    await service.sync_index()

    assert store.add_calls == 1
    assert first_ids == ["man_txt:1:0"]

    service.manifest = [_manual(version=2)]
    await service.sync_index()
    assert first_ids[0] in store.deleted_ids
    assert "man_txt:2:0" in store.documents
    assert store.add_calls == 2

    service.manifest = []
    await service.sync_index()
    assert store.documents == {}


@pytest.mark.asyncio
async def test_search_applies_top_k_and_score_threshold(tmp_path: Path) -> None:
    store = MemoryVectorStore()
    metadata = {
        "source_id": 8,
        "document_id": "man_txt",
        "title": "文本手册",
        "source_locator": "manual:man_txt",
        "page": 2,
    }
    store.search_results = [
        (Document(page_content="高相关内容", metadata=metadata), 0.82),
        (Document(page_content="低相关内容", metadata=metadata), 0.2),
    ]
    service = ManifestRagService(_settings(tmp_path), store)

    results = await service.search("打印机")

    assert store.last_k == 5
    assert [result.content for result in results] == ["高相关内容"]


@pytest.mark.asyncio
async def test_embedding_failure_aborts_sync_instead_of_exposing_stale_index(
    tmp_path: Path,
) -> None:
    manual_path = tmp_path / "0123456789abcdef0123456789abcdef.txt"
    manual_path.write_text("旧版本内容", encoding="utf-8")
    store = MemoryVectorStore()
    old = Document(
        page_content="旧版本内容",
        metadata={"document_id": "man_txt", "fingerprint": "old:1"},
    )
    store.documents["man_txt:1:0"] = old
    store.fail_add = True
    service = ManifestRagService(_settings(tmp_path), store)
    service.manifest = [_manual(version=2)]

    with pytest.raises(RagUnavailableError, match="Chroma 手册索引同步失败"):
        await service.sync_index()

    assert "man_txt:1:0" in store.deleted_ids
    assert store.documents == {}
