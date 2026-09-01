"""后端活动角色切换测试。"""

import json
from collections.abc import AsyncIterator, Sequence
from pathlib import Path

from fastapi.testclient import TestClient
from langchain_core.messages import BaseMessage

from agent_service.config import Settings
from agent_service.main import create_app


class PromptEchoModelGateway:
    """把收到的系统提示词作为回答返回，用于验证角色确实进入模型输入。"""

    @property
    def model_name(self) -> str:
        return "prompt-echo-model"

    async def stream(
        self,
        messages: Sequence[BaseMessage],
        *,
        max_output_tokens: int,
    ) -> AsyncIterator[str]:
        del max_output_tokens
        yield str(messages[0].content)


def _write_role(path: Path, *, name: str, system_prompt: str) -> None:
    """向 pytest 临时目录写入合法角色文件。"""

    path.write_text(
        json.dumps(
            {
                "name": name,
                "description": "测试角色",
                "systemPrompt": system_prompt,
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )


def _chat_body(request_id: int) -> dict[str, object]:
    """创建不包含任何角色参数的聊天请求。"""

    return {
        "requestId": request_id,
        "sessionId": 3,
        "userId": 7,
        "message": "请回答测试问题",
        "history": None,
        "policy": {
            "modelRoute": "test-model",
            "knowledgeBaseIds": ["test-kb"],
            "toolsEnabled": False,
            "maxOutputTokens": 128,
        },
    }


def _response_text(response_content: str) -> str:
    """拼接 SSE 中的所有 delta 文本。"""

    text_parts: list[str] = []
    for line in response_content.splitlines():
        if not line.startswith("data: "):
            continue
        event = json.loads(line.removeprefix("data: "))
        if event["event"] == "delta":
            text_parts.append(event["payload"]["content"])
    return "".join(text_parts)


def test_role_file_changes_take_effect_without_restart(tmp_path: Path) -> None:
    """修改后端文件后，下一次请求应使用新角色，且请求体不包含 roleId。"""

    role_path = tmp_path / "active_role.json"
    _write_role(role_path, name="角色甲", system_prompt="这是角色甲的系统提示词")
    settings = Settings(
        _env_file=None,
        environment="test",
        model_provider="mock",
        model_name="mock-model",
        role_config_path=role_path,
        context_storage_enabled=False,
    )

    with TestClient(
        create_app(settings=settings, model_gateway=PromptEchoModelGateway())
    ) as client:
        first = client.post("/internal/ai/v1/chat/stream", json=_chat_body(101))
        _write_role(role_path, name="角色乙", system_prompt="这是角色乙的系统提示词")
        second = client.post("/internal/ai/v1/chat/stream", json=_chat_body(102))

    assert first.status_code == 200
    assert second.status_code == 200
    assert _response_text(first.text) == "这是角色甲的系统提示词"
    assert _response_text(second.text) == "这是角色乙的系统提示词"
    assert "roleId" not in _chat_body(103)


def test_invalid_role_file_is_rejected_before_sse(tmp_path: Path) -> None:
    """角色文件无效时，应在建立 SSE 前返回清晰的配置错误。"""

    role_path = tmp_path / "active_role.json"
    role_path.write_text('{"name":"缺少 systemPrompt"}', encoding="utf-8")
    settings = Settings(
        _env_file=None,
        environment="test",
        model_provider="mock",
        model_name="mock-model",
        role_config_path=role_path,
        context_storage_enabled=False,
    )

    with TestClient(
        create_app(settings=settings, model_gateway=PromptEchoModelGateway())
    ) as client:
        response = client.post("/internal/ai/v1/chat/stream", json=_chat_body(201))

    assert response.status_code == 500
    assert response.json()["detail"]["code"] == "ROLE_CONFIG_INVALID"
