"""FastAPI 应用工厂和资源生命周期。"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.checkpoint.redis.ashallow import AsyncShallowRedisSaver
from langgraph.store.mongodb import MongoDBStore
from redis.asyncio import Redis

from agent_service import __version__
from agent_service.api.routes import chat, health
from agent_service.config import Settings, get_settings
from agent_service.models.gateway import ModelGateway, create_model_gateway
from agent_service.services.agent_runtime import AgentRuntime
from agent_service.services.context_archive import ContextArchive, DisabledContextArchive
from agent_service.services.manual_rag import ManualRagService, create_manual_rag_service
from agent_service.services.role_profile import RoleProfileProvider


def create_app(
    *,
    settings: Settings | None = None,
    model_gateway: ModelGateway | None = None,
    rag_service: ManualRagService | None = None,
) -> FastAPI:
    """创建 FastAPI 应用，并按配置选择内存或持久化运行模式。"""

    resolved = settings or get_settings()
    resolved_model_gateway = model_gateway or create_model_gateway(resolved)
    resolved_rag_service = rag_service
    if resolved.rag_enabled and resolved_rag_service is None:
        resolved_rag_service = create_manual_rag_service(resolved)
    role_provider = RoleProfileProvider(resolved.role_config_path)
    logging.basicConfig(
        level=getattr(logging, resolved.log_level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        if not resolved.context_storage_enabled:
            # 测试和纯本地开发不应访问开发者 .env 中的真实数据库。InMemorySaver 仍然
            # 保留同一进程内的多轮消息，因此能真实验证 LangGraph thread 行为。
            app.state.agent_runtime = AgentRuntime(
                resolved_model_gateway,
                role_provider,
                checkpointer=InMemorySaver(),
                archive=DisabledContextArchive(),
                redis=None,
                model_max_input_tokens=resolved.model_max_input_tokens,
                session_lock_timeout_seconds=resolved.session_lock_timeout_seconds,
                rag_service=resolved_rag_service,
            )
            yield
            return

        # Settings 的 after-validator 已保证持久化模式下两个 URI 均存在。assert 既帮助
        # 类型检查器完成收窄，也能防止未来绕过 Settings 校验后出现 NoneType 异常。
        assert resolved.redis_url is not None
        assert resolved.mongodb_uri is not None
        redis_url = resolved.redis_url.get_secret_value()
        mongodb_uri = resolved.mongodb_uri.get_secret_value()

        # Saver 自己管理 Redis checkpoint key；应用不再拼接或覆盖这些 key。
        # AsyncShallowRedisSaver.__aenter__ 已经调用 asetup() 创建索引，不能重复调用。
        # Stream 和分布式锁使用独立 redis-py 客户端。两个异步上下文确保后续初始化
        # 失败时，checkpoint 客户端与通用 Redis 连接池都能按进入顺序安全关闭。
        async with (
            AsyncShallowRedisSaver.from_conn_string(
                redis_url,
                ttl={
                    "default_ttl": resolved.context_redis_ttl_minutes,
                    "refresh_on_read": True,
                },
            ) as checkpointer,
            Redis.from_url(redis_url, decode_responses=True) as redis,
        ):
            await redis.ping()

            # MongoDBStore.from_conn_string() 在进入上下文时就会创建集合和唯一索引；
            # 当前版本没有 setup() 方法，也不需要额外初始化调用。
            with MongoDBStore.from_conn_string(
                conn_string=mongodb_uri,
                db_name=resolved.mongodb_database,
                collection_name=resolved.mongodb_context_collection,
            ) as store:
                archive = ContextArchive(
                    redis=redis,
                    store=store,
                    stream_name=resolved.context_archive_stream,
                    rehydrate_turn_limit=resolved.context_rehydrate_turn_limit,
                )
                app.state.agent_runtime = AgentRuntime(
                    resolved_model_gateway,
                    role_provider,
                    checkpointer=checkpointer,
                    archive=archive,
                    redis=redis,
                    model_max_input_tokens=resolved.model_max_input_tokens,
                    session_lock_timeout_seconds=resolved.session_lock_timeout_seconds,
                    rag_service=resolved_rag_service,
                )
                yield

    app = FastAPI(
        title="XinChuang Agent Service",
        version=__version__,
        description="信创智能客服独立 LangChain/LangGraph 智能体服务",
        lifespan=lifespan,
    )
    app.state.settings = resolved
    app.include_router(health.router, prefix="/internal/ai/v1")
    app.include_router(chat.router, prefix="/internal/ai/v1")
    return app
