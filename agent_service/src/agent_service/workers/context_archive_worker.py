"""把 Redis Stream 中的完成轮次幂等写入 MongoDBStore。"""

import asyncio
import json
import logging

from langgraph.store.base import BaseStore
from redis.asyncio import Redis
from redis.exceptions import ResponseError

from agent_service.services.context_archive import conversation_namespace

logger = logging.getLogger(__name__)


class ContextArchiveWorker:
    """消费完成轮次事件，并在 MongoDB 写入成功后确认消息。"""

    def __init__(
        self,
        *,
        redis: Redis,
        store: BaseStore,
        stream: str,
        group: str,
        consumer: str,
    ) -> None:
        self.redis = redis
        self.store = store
        self.stream = stream
        self.group = group
        self.consumer = consumer

    async def ensure_group(self) -> None:
        """首次启动创建 consumer group；已存在时忽略 BUSYGROUP。"""

        try:
            await self.redis.xgroup_create(
                self.stream,
                self.group,
                id="0-0",
                mkstream=True,
            )
        except ResponseError as exc:
            if "BUSYGROUP" not in str(exc):
                raise

    async def persist(self, message_id: str, fields: dict[str, str]) -> None:
        """requestId 是 Store key；重复投递只覆盖同一个文档。"""

        event = json.loads(fields["event"])
        namespace = conversation_namespace(
            int(event["user_id"]),
            int(event["session_id"]),
        )
        await self.store.aput(
            namespace,
            str(event["request_id"]),
            event,
            index=False,
        )
        # MongoDBStore 成功后才能 ACK；失败时消息继续留在 Pending。
        await self.redis.xack(self.stream, self.group, message_id)

    async def reclaim_stale(self) -> None:
        """接管崩溃消费者留下的 Pending 消息。

        Redis 8 返回游标、成功接管的消息和已从 Stream 删除的消息 ID。删除 ID 表示
        Pending 引用对应的消息体已不存在，需要记录告警以便检查人工裁剪或误删除。
        """

        cursor = "0-0"
        while True:
            result = await self.redis.xautoclaim(
                self.stream,
                self.group,
                self.consumer,
                min_idle_time=60_000,
                start_id=cursor,
                count=50,
            )
            cursor, messages = result[0], result[1]
            deleted_ids = result[2] if len(result) > 2 else []
            if deleted_ids:
                logger.warning(
                    "发现已删除的 Pending 上下文消息，stream=%s ids=%s",
                    self.stream,
                    deleted_ids,
                )
            for message_id, fields in messages:
                try:
                    await self.persist(message_id, fields)
                except Exception:
                    # 接管失败时保留 Pending，不能因为一条坏消息终止整个消费者进程。
                    logger.exception("接管的上下文归档失败，messageId=%s", message_id)
            if cursor in ("0-0", b"0-0"):
                return

    async def run(self) -> None:
        """持续消费新消息，并周期性接管其他崩溃消费者的 Pending。"""

        await self.ensure_group()
        await self.reclaim_stale()
        loop = asyncio.get_running_loop()
        next_reclaim_at = loop.time() + 60
        while True:
            batches = await self.redis.xreadgroup(
                self.group,
                self.consumer,
                {self.stream: ">"},
                count=50,
                block=5_000,
            )
            for _stream_name, messages in batches:
                for message_id, fields in messages:
                    try:
                        await self.persist(message_id, fields)
                    except Exception:
                        # 不 ACK；记录后由本消费者重试或由 XAUTOCLAIM 接管。
                        logger.exception("上下文归档失败，messageId=%s", message_id)
                        await asyncio.sleep(1)

            # 只在启动时 XAUTOCLAIM 会漏掉“本进程运行期间其他消费者崩溃”的消息。
            # 每分钟扫描一次，保证这些 Pending 最终能够被健康消费者接管。
            if loop.time() >= next_reclaim_at:
                await self.reclaim_stale()
                next_reclaim_at = loop.time() + 60
