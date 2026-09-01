"""运行配置校验测试。"""

import pytest
from pydantic import ValidationError

from agent_service.config import Settings


def test_storage_can_be_disabled_without_external_connections() -> None:
    """测试模式关闭持久化后，不应强制要求 Redis 或 MongoDB URI。"""

    settings = Settings(
        _env_file=None,
        environment="test",
        model_provider="mock",
        context_storage_enabled=False,
    )

    assert settings.redis_url is None
    assert settings.mongodb_uri is None


def test_redis_search_requires_database_zero() -> None:
    """在启动前拒绝 Redis Search 无法使用的非 0 逻辑数据库。"""

    with pytest.raises(ValidationError, match="database 0"):
        Settings(
            _env_file=None,
            context_storage_enabled=True,
            redis_url="redis://localhost:6379/4",
            mongodb_uri="mongodb://localhost:27017",
        )


def test_database_zero_context_configuration_is_accepted() -> None:
    """合法 URI 应通过纯配置校验，不在该测试中访问网络。"""

    settings = Settings(
        _env_file=None,
        context_storage_enabled=True,
        redis_url="redis://localhost:6379/0",
        mongodb_uri="mongodb://localhost:27017",
    )

    assert settings.redis_url is not None
