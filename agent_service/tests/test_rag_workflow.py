"""LangGraph 语义路由、查询重写、检索和引用事件测试。"""

import json
from collections.abc import AsyncIterator, Sequence

from fastapi.testclient import TestClient
from langchain_core.messages import BaseMessage

from agent_service.config import Settings
from agent_service.core.exceptions import RagUnavailableError
from agent_service.main import create_app
from agent_service.schemas.rag import RetrievedManualChunk


class WorkflowGateway:
    """按提示词返回确定结果，并记录最终生成输入。"""

    def __init__(self, route: str, *, fail_rewrite: bool = False) -> None:
        self.route = route
        self.fail_rewrite = fail_rewrite
        self.generated_messages: Sequence[BaseMessage] = []

    @property
    def model_name(self) -> str:
        return "workflow-test-model"

    async def complete(
        self,
        messages: Sequence[BaseMessage],
        *,
        max_output_tokens: int,
    ) -> str:
        del max_output_tokens
        system_text = str(messages[0].content)
        if "只输出 DIRECT 或 RAG" in system_text:
            return self.route
        if self.fail_rewrite:
            raise RuntimeError("rewrite unavailable")
        return "统信 UOS 打印机驱动安装失败"

    async def stream(
        self,
        messages: Sequence[BaseMessage],
        *,
        max_output_tokens: int,
    ) -> AsyncIterator[str]:
        del max_output_tokens
        self.generated_messages = messages
        yield "测试回答"


class WorkflowRagService:
    """记录工作流调用顺序的内存 RAG 替身。"""

    def __init__(self, *, fail_sync: bool = False) -> None:
        self.fail_sync = fail_sync
        self.sync_count = 0
        self.queries: list[str] = []

    async def sync_index(self) -> None:
        self.sync_count += 1
        if self.fail_sync:
            raise RagUnavailableError("test unavailable")

    async def search(self, query: str) -> list[RetrievedManualChunk]:
        self.queries.append(query)
        return [
            RetrievedManualChunk(
                source_id=9,
                document_id="man_uos",
                title="UOS 驱动手册",
                content="安装驱动前先确认设备型号。",
                source_locator="manual:man_uos",
                page=3,
                score=0.88,
            )
        ]


def _settings() -> Settings:
    return Settings(
        _env_file=None,
        environment="test",
        model_provider="mock",
        model_name="mock-model",
        context_storage_enabled=False,
        rag_enabled=True,
        embedding_api_key="test-key",
    )


def _request(message: str) -> dict[str, object]:
    return {
        "requestId": 31,
        "sessionId": 3,
        "userId": 7,
        "message": message,
        "history": [],
        "policy": {
            "modelRoute": "test",
            "knowledgeBaseIds": ["default"],
            "toolsEnabled": False,
            "maxOutputTokens": 128,
        },
    }


def _events(response_text: str) -> list[dict[str, object]]:
    return [
        json.loads(line.removeprefix("data: "))
        for line in response_text.splitlines()
        if line.startswith("data: ")
    ]


def test_direct_route_skips_rewrite_and_retrieval() -> None:
    gateway = WorkflowGateway("DIRECT")
    rag = WorkflowRagService()
    app = create_app(settings=_settings(), model_gateway=gateway, rag_service=rag)
    with TestClient(app) as client:
        response = client.post("/internal/ai/v1/chat/stream", json=_request("你好"))

    assert response.status_code == 200
    assert rag.sync_count == 0
    assert rag.queries == []
    assert all(event["event"] != "citation" for event in _events(response.text))


def test_rag_route_rewrites_retrieves_and_emits_citation() -> None:
    gateway = WorkflowGateway("RAG")
    rag = WorkflowRagService()
    app = create_app(settings=_settings(), model_gateway=gateway, rag_service=rag)
    with TestClient(app) as client:
        response = client.post(
            "/internal/ai/v1/chat/stream",
            json=_request("这个驱动还是装不上怎么办？"),
        )

    events = _events(response.text)
    names = [event["event"] for event in events]
    citation = next(event for event in events if event["event"] == "citation")
    source = citation["payload"]["sources"][0]  # type: ignore[index]

    assert rag.sync_count == 1
    assert rag.queries == ["统信 UOS 打印机驱动安装失败"]
    assert names.index("citation") < names.index("delta")
    assert source["sourceLocator"] == "manual:man_uos"
    assert source["page"] == 3
    assert "安装驱动前先确认设备型号" in str(gateway.generated_messages[0].content)


def test_manifest_failure_degrades_without_using_vector_search() -> None:
    gateway = WorkflowGateway("RAG")
    rag = WorkflowRagService(fail_sync=True)
    app = create_app(settings=_settings(), model_gateway=gateway, rag_service=rag)
    with TestClient(app) as client:
        response = client.post("/internal/ai/v1/chat/stream", json=_request("打印机故障"))

    events = _events(response.text)
    status_messages = [
        event["payload"]["message"]  # type: ignore[index]
        for event in events
        if event["event"] == "status"
    ]
    assert rag.queries == []
    assert "知识库暂时不可用，将基于通用能力回答" in status_messages
    assert all(event["event"] != "citation" for event in events)


def test_rewrite_failure_falls_back_to_original_question() -> None:
    gateway = WorkflowGateway("RAG", fail_rewrite=True)
    rag = WorkflowRagService()
    app = create_app(settings=_settings(), model_gateway=gateway, rag_service=rag)
    with TestClient(app) as client:
        response = client.post(
            "/internal/ai/v1/chat/stream",
            json=_request("这个驱动还是装不上怎么办？"),
        )

    assert response.status_code == 200
    assert rag.queries == ["这个驱动还是装不上怎么办？"]
