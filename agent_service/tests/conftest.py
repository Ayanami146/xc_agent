"""测试共享夹具。"""

import pytest
from fastapi.testclient import TestClient

from agent_service.config import Settings
from agent_service.main import create_app
from agent_service.models.gateway import MockModelGateway


@pytest.fixture
def client() -> TestClient:
    """创建不访问网络、不读取开发者本地 .env 的测试客户端。"""

    settings = Settings(
        _env_file=None,
        environment="test",
        model_provider="mock",
        model_name="mock-model",
        internal_auth_enabled=False,
        context_storage_enabled=False,
    )
    with TestClient(create_app(settings=settings, model_gateway=MockModelGateway())) as test_client:
        yield test_client


@pytest.fixture
def chat_request() -> dict[str, object]:
    """返回与 Java ``AiChatStreamDTO`` 对齐的合法请求。"""

    return {
        "requestId": 21,
        "sessionId": 3,
        "userId": 7,
        "message": "UOS 打印机无法识别怎么办？",
        "history": [],
        "policy": {
            "modelRoute": "customer-service-default",
            "knowledgeBaseIds": ["support-kb"],
            "toolsEnabled": False,
            "maxOutputTokens": 256,
        },
    }
