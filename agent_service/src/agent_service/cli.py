"""uv 项目的命令行启动入口。"""

import uvicorn

from agent_service.config import get_settings


def main() -> None:
    """按环境配置启动 Uvicorn。

    使用应用工厂避免模块导入时立刻读取 .env、创建模型客户端。这样测试导入
    create_app 时不会意外依赖本机 Redis、MongoDB 或真实模型配置。
    """

    settings = get_settings()
    uvicorn.run(
        "agent_service.main:create_app",
        factory=True,
        host=settings.host,
        port=settings.port,
        log_level=settings.log_level.lower(),
        reload=settings.environment == "development",
    )


if __name__ == "__main__":
    main()
