"""健康检查接口。"""

from fastapi import APIRouter, Request

from agent_service import __version__
from agent_service.config import Settings

router = APIRouter(tags=["health"])


@router.get("/health")
async def health(request: Request) -> dict[str, str]:
    """返回进程存活状态和非敏感运行信息。"""

    settings: Settings = request.app.state.settings
    return {
        "status": "UP",
        "service": settings.app_name,
        "version": __version__,
        "environment": settings.environment,
        "modelProvider": settings.model_provider,
    }
