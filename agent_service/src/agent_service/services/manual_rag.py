"""维修手册清单同步、文档解析和 Chroma 稠密检索。"""

import asyncio
import logging
from typing import Any

import httpx
from langchain_chroma import Chroma
from langchain_community.document_loaders import Docx2txtLoader, PyPDFLoader, TextLoader
from langchain_core.documents import Document
from langchain_openai import OpenAIEmbeddings
from langchain_text_splitters import RecursiveCharacterTextSplitter
from pydantic import ValidationError

from agent_service.config import Settings
from agent_service.core.exceptions import RagUnavailableError
from agent_service.schemas.rag import RagManual, RetrievedManualChunk

logger = logging.getLogger(__name__)


class ManualRagService:
    """将 Java 的已发布清单增量同步到本地 Chroma，并执行稠密检索。

    Java MySQL 和共享目录中的原文件是唯一真相源。Chroma 仅保存可删除、可重建的派生
    切片；本服务不读取 Java FAQ Redis，也不使用 Agent 上下文 Redis 保存索引状态。
    """

    def __init__(self, settings: Settings, vector_store: Chroma) -> None:
        self._settings = settings
        self._vector_store = vector_store
        self._manual_root = settings.manual_storage_directory.resolve()
        self._splitter = RecursiveCharacterTextSplitter(
            chunk_size=settings.rag_chunk_size,
            chunk_overlap=settings.rag_chunk_overlap,
        )
        self._sync_lock = asyncio.Lock()

    async def sync_index(self) -> None:
        """拉取当前发布清单，并在进程内串行完成增量同步。"""

        async with self._sync_lock:
            manuals = await self._fetch_manifest()
            try:
                await asyncio.to_thread(self._sync_index_blocking, manuals)
            except RagUnavailableError:
                raise
            except Exception as exc:
                raise RagUnavailableError("Chroma 手册索引同步失败") from exc

    async def search(self, query: str) -> list[RetrievedManualChunk]:
        """对已同步的 Chroma 集合执行 TopK 稠密相似度检索。"""

        try:
            results = await asyncio.to_thread(
                self._vector_store.similarity_search_with_relevance_scores,
                query,
                k=self._settings.rag_top_k,
            )
        except Exception as exc:
            raise RagUnavailableError("维修手册向量检索失败") from exc

        chunks: list[RetrievedManualChunk] = []
        for document, score in results:
            if score < self._settings.rag_score_threshold:
                continue
            metadata = document.metadata
            chunks.append(
                RetrievedManualChunk(
                    source_id=int(metadata["source_id"]),
                    document_id=str(metadata["document_id"]),
                    title=str(metadata["title"]),
                    content=document.page_content,
                    source_locator=str(metadata["source_locator"]),
                    page=int(metadata["page"]) if metadata.get("page") is not None else None,
                    score=float(score),
                )
            )
        return chunks

    async def _fetch_manifest(self) -> list[RagManual]:
        """通过内部 Token 获取 Java 当前已发布手册，不接受本地目录扫描替代。"""

        headers: dict[str, str] = {}
        token = self._settings.internal_token.get_secret_value()
        if token:
            headers["X-Internal-Token"] = token
        try:
            async with httpx.AsyncClient(timeout=self._settings.model_timeout_seconds) as client:
                response = await client.get(self._settings.java_rag_manifest_url, headers=headers)
                response.raise_for_status()
                payload = response.json()
                if not isinstance(payload, list):
                    raise ValueError("Java 手册清单必须是数组")
                manuals: list[RagManual] = []
                for item in payload:
                    try:
                        manuals.append(RagManual.model_validate(item))
                    except ValidationError as exc:
                        # 历史坏记录不应阻断其余有效手册；跳过后同步仍会删除其陈旧切片。
                        logger.warning("忽略不符合 RAG 契约的手册清单项：%s", exc)
                return manuals
        except (httpx.HTTPError, ValidationError, ValueError) as exc:
            raise RagUnavailableError("Java 已发布手册清单暂时不可用") from exc

    def _sync_index_blocking(self, manuals: list[RagManual]) -> None:
        """比较 Chroma 元数据，只重建新增或发生变化的手册。"""

        snapshot = self._vector_store.get(include=["metadatas"])
        ids = list(snapshot.get("ids") or [])
        metadatas = list(snapshot.get("metadatas") or [])
        existing_by_document: dict[str, list[tuple[str, dict[str, Any]]]] = {}
        for chunk_id, metadata in zip(ids, metadatas, strict=False):
            if not metadata or "document_id" not in metadata:
                continue
            existing_by_document.setdefault(str(metadata["document_id"]), []).append(
                (str(chunk_id), metadata)
            )

        current_by_document = {manual.document_id: manual for manual in manuals}
        for document_id, existing in existing_by_document.items():
            manual = current_by_document.get(document_id)
            unchanged = manual is not None and all(
                str(metadata.get("fingerprint")) == manual.fingerprint
                for _, metadata in existing
            )
            if not unchanged:
                self._vector_store.delete(ids=[chunk_id for chunk_id, _ in existing])

        for manual in manuals:
            existing = existing_by_document.get(manual.document_id, [])
            if existing and all(
                str(metadata.get("fingerprint")) == manual.fingerprint
                for _, metadata in existing
            ):
                continue

            try:
                chunks = self._load_and_split(manual)
            except (OSError, ValueError, RuntimeError) as exc:
                # 单个损坏文件不应阻断其余已发布手册；该文档保持无切片，下次检索会重试。
                logger.warning("维修手册解析失败，documentId=%s error=%s", manual.document_id, exc)
                continue
            chunk_ids = [
                f"{manual.document_id}:{manual.resource_version}:{index}"
                for index in range(len(chunks))
            ]
            if chunks:
                self._vector_store.add_documents(chunks, ids=chunk_ids)

    def _load_and_split(self, manual: RagManual) -> list[Document]:
        """从共享目录加载受支持文件，并为每个切片补充引用所需元数据。"""

        path = (self._manual_root / manual.object_key).resolve()
        if path.parent != self._manual_root or not path.is_file():
            raise ValueError("维修手册文件不存在或路径无效")

        suffix = path.suffix.lower()
        if suffix == ".pdf":
            documents = PyPDFLoader(str(path)).load()
        elif suffix == ".docx":
            documents = Docx2txtLoader(str(path)).load()
        elif suffix in {".txt", ".md"}:
            documents = TextLoader(str(path), encoding="utf-8", autodetect_encoding=True).load()
        else:
            raise ValueError("维修手册格式不受支持")

        for document in documents:
            raw_page = document.metadata.get("page")
            page = int(raw_page) + 1 if raw_page is not None else None
            metadata: dict[str, Any] = {
                "source_id": manual.source_id,
                "document_id": manual.document_id,
                "title": manual.title,
                "file_name": manual.file_name,
                "source_locator": f"manual:{manual.document_id}",
                "fingerprint": manual.fingerprint,
            }
            # Chroma metadata 不接受 None，因此无页码格式直接省略该字段。
            if page is not None:
                metadata["page"] = page
            document.metadata = metadata
        return self._splitter.split_documents(documents)


def create_manual_rag_service(settings: Settings) -> ManualRagService:
    """依据配置创建阿里云 Embedding 和本地持久化 Chroma。"""

    assert settings.embedding_api_key is not None
    settings.chroma_directory.mkdir(parents=True, exist_ok=True)
    embeddings = OpenAIEmbeddings(
        model=settings.embedding_model,
        api_key=settings.embedding_api_key.get_secret_value(),
        base_url=settings.embedding_base_url,
        dimensions=settings.embedding_dimensions,
        chunk_size=10,
        # langchain-openai 默认会先按 OpenAI 的 token 规则把长文本转换为整数数组。
        # 阿里云 OpenAI 兼容接口的 embedding input 只接受字符串或字符串列表，
        # 若保留默认值会返回 “contents is neither str nor list of str” 的 400 错误。
        # 手册已在进入向量化前由 RecursiveCharacterTextSplitter 完成切片，因此这里
        # 直接提交切片文本即可，也避免了对 text-embedding-v3 使用不匹配的 tokenizer。
        check_embedding_ctx_length=False,
    )
    vector_store = Chroma(
        collection_name=settings.chroma_collection,
        embedding_function=embeddings,
        persist_directory=str(settings.chroma_directory),
        collection_metadata={"hnsw:space": "cosine"},
    )
    return ManualRagService(settings, vector_store)
