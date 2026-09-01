"""内部接口依赖。"""

from hmac import compare_digest

from fastapi import Header, HTTPException, Request, status

from agent_service.config import Settings


async def verify_internal_token(
    request: Request,
    x_internal_token: str | None = Header(default=None),
) -> None:
    """验证最小内部服务令牌。

    这是开发骨架的最小保护，不等同于最终 HMAC 方案。生产阶段应加入时间戳、nonce、请求体
    摘要和 Redis 防重放。即使关闭鉴权，也只应把服务绑定到可信内网地址。
    """

    settings: Settings = request.app.state.settings
    if not settings.internal_auth_enabled:
        return

    expected = settings.internal_token.get_secret_value()
    if x_internal_token is None or not compare_digest(x_internal_token, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={"code": "INTERNAL_AUTH_INVALID", "message": "内部服务认证失败"},
        )
