"""MongoDB 上下文归档消费者进程入口。"""

import asyncio

from langgraph.store.mongodb import MongoDBStore
from redis.asyncio import Redis

from agent_service.config import get_settings
from agent_service.workers.context_archive_worker import ContextArchiveWorker


async def main() -> None:
    """连接 Redis 和 MongoDB，并持续运行归档消费者。"""

    settings = get_settings()
    if not settings.context_storage_enabled:
        raise RuntimeError("上下文持久化已关闭，不能启动归档消费者")

    # Settings 已在持久化模式下验证两个 URI。assert 用于类型收窄，并防止未来绕过
    # 配置工厂时产生模糊的 NoneType 异常。
    assert settings.redis_url is not None
    assert settings.mongodb_uri is not None
    redis_url = settings.redis_url.get_secret_value()
    mongodb_uri = settings.mongodb_uri.get_secret_value()

    # 异步上下文确保进程退出或 MongoDB 初始化失败时，Redis 连接池也会正确关闭。
    async with Redis.from_url(redis_url, decode_responses=True) as redis:
        await redis.ping()

        # MongoDBStore.from_conn_string() 已在进入上下文时创建集合及唯一索引，
        # 当前版本没有 setup() 方法。
        with MongoDBStore.from_conn_string(
            conn_string=mongodb_uri,
            db_name=settings.mongodb_database,
            collection_name=settings.mongodb_context_collection,
        ) as store:
            worker = ContextArchiveWorker(
                redis=redis,
                store=store,
                stream=settings.context_archive_stream,
                group=settings.context_archive_group,
                consumer=settings.context_archive_consumer,
            )
            await worker.run()


def run() -> None:
    asyncio.run(main())
