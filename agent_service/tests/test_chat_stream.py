"""流式聊天和取消接口测试。"""

import json

from fastapi.testclient import TestClient

from agent_service.config import Settings
from agent_service.main import create_app
from agent_service.models.gateway import MockModelGateway


def _parse_sse_data(lines: list[str]) -> list[dict[str, object]]:
    """从测试客户端返回行中提取所有 data JSON。"""

    return [json.loads(line.removeprefix("data: ")) for line in lines if line.startswith("data: ")]


def test_chat_stream_emits_ordered_terminal_sequence(
    client: TestClient,
    chat_request: dict[str, object],
) -> None:
    """最小图应产生递增 sequence，并且只以 done 正常结束。"""

    with client.stream(
        "POST",
        "/internal/ai/v1/chat/stream",
        json=chat_request,
    ) as response:
        lines = list(response.iter_lines())

    events = _parse_sse_data(lines)
    event_names = [event["event"] for event in events]
    sequences = [event["sequence"] for event in events]

    assert response.status_code == 200
    assert event_names[0] == "meta"
    assert "status" in event_names
    assert "delta" in event_names
    assert event_names[-1] == "done"
    assert "error" not in event_names
    assert sequences == list(range(1, len(events) + 1))


def test_cancel_unknown_run_is_idempotent(client: TestClient) -> None:
    """取消不存在的运行不应报错，方便 Java 安全重试。"""

    response = client.post("/internal/ai/v1/chat/requests/999/cancel")

    assert response.status_code == 200
    assert response.json() == {
        "requestId": 999,
        "cancelRequested": False,
        "status": "NOT_RUNNING",
    }


def test_cancel_path_uses_java_camel_case_contract(client: TestClient) -> None:
    """OpenAPI 路径参数必须保持 Java 文档中的 requestId，而不是 Python 的 snake_case。"""

    paths = client.app.openapi()["paths"]

    assert "/internal/ai/v1/chat/requests/{requestId}/cancel" in paths
    assert "/internal/ai/v1/chat/requests/{request_id}/cancel" not in paths


def test_history_null_is_accepted_but_not_used(
    client: TestClient,
    chat_request: dict[str, object],
) -> None:
    """兼容 Java 发送 history=null，同时继续由 Agent 自己负责上下文来源。"""

    chat_request["history"] = None
    with client.stream(
        "POST",
        "/internal/ai/v1/chat/stream",
        json=chat_request,
    ) as response:
        events = _parse_sse_data(list(response.iter_lines()))

    assert response.status_code == 200
    assert events[-1]["event"] == "done"


def test_reused_request_id_does_not_break_message_order(
    client: TestClient,
    chat_request: dict[str, object],
) -> None:
    """开发环境重建 MySQL 后，旧 checkpoint 中可能已存在相同 requestId。

    LangGraph 的消息 reducer 会按消息 id 原位替换。如果当前输入与旧用户消息同 id，
    旧助手消息仍可能留在列表末尾，因此这里用连续两次相同请求覆盖该回归场景。
    """

    terminal_events: list[str] = []
    for _ in range(2):
        with client.stream(
            "POST",
            "/internal/ai/v1/chat/stream",
            json=chat_request,
        ) as response:
            events = _parse_sse_data(list(response.iter_lines()))

        assert response.status_code == 200
        terminal_events.append(str(events[-1]["event"]))

    assert terminal_events == ["done", "done"]


def test_internal_token_can_be_enabled() -> None:
    """开启内部认证后必须拒绝缺失令牌，并接受匹配的令牌。"""

    settings = Settings(
        _env_file=None,
        environment="test",
        model_provider="mock",
        model_name="mock-model",
        internal_auth_enabled=True,
        internal_token="test-internal-secret",
        context_storage_enabled=False,
    )
    with TestClient(create_app(settings=settings, model_gateway=MockModelGateway())) as auth_client:
        denied = auth_client.post("/internal/ai/v1/chat/requests/999/cancel")
        accepted = auth_client.post(
            "/internal/ai/v1/chat/requests/999/cancel",
            headers={"X-Internal-Token": "test-internal-secret"},
        )

    assert denied.status_code == 401
    assert accepted.status_code == 200


def test_invalid_chat_request_is_rejected_before_stream(client: TestClient) -> None:
    """requestId 等契约错误必须在建立 SSE 前返回 HTTP 422。"""

    response = client.post(
        "/internal/ai/v1/chat/stream",
        json={"requestId": 0},
    )

    assert response.status_code == 422


def test_blank_message_is_rejected_before_stream(
    client: TestClient,
    chat_request: dict[str, object],
) -> None:
    """只包含空白符的消息不能进入 LangGraph。"""

    chat_request["message"] = "   \n\t"
    response = client.post(
        "/internal/ai/v1/chat/stream",
        json=chat_request,
    )

    assert response.status_code == 422
