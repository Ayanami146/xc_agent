"""Redis Streams -> MongoDBStore 的上下文归档边界。"""

import json
import logging
from collections.abc import Sequence
from datetime import UTC, datetime
from typing import Protocol

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage
from langgraph.store.base import BaseStore
from pymongo.errors import PyMongoError
from redis.asyncio import Redis
from redis.exceptions import RedisError

from agent_service.core.exceptions import (
    ContextArchiveUnavailableError,
    ContextStoreUnavailableError,
)

logger = logging.getLogger(__name__)


def conversation_namespace(user_id: int, session_id: int) -> tuple[str, ...]:
    """MongoDBStore 的层级 namespace；用户与会话天然隔离。"""

    return ("users", str(user_id), "conversations", str(session_id), "turns")


class ContextArchiveBackend(Protocol):
    """AgentRuntime 依赖的最小归档能力。

    生产环境使用 Redis/MongoDB 实现；关闭持久化的测试环境使用空实现。通过协议隔离后，
    FastAPI 测试不需要连接开发者本机的数据库。
    """

    async def publish_completed_turn(
        self,
        *,
        request_id: int,
        user_id: int,
        session_id: int,
        user_text: str,
        assistant_text: str,
    ) -> str | None: ...

    async def load_messages(
        self,
        *,
        user_id: int,
        session_id: int,
    ) -> Sequence[BaseMessage]: ...


class DisabledContextArchive:
    """持久化关闭时使用的空归档实现。

    此模式只用于单进程开发和单元测试。对话热状态仍由 InMemorySaver 保存，但进程退出后
    不会恢复，避免测试意外访问真实 Redis 或 MongoDB。
    """

    async def publish_completed_turn(
        self,
        *,
        request_id: int,
        user_id: int,
        session_id: int,
        user_text: str,
        assistant_text: str,
    ) -> None:
        del request_id, user_id, session_id, user_text, assistant_text

    async def load_messages(
        self,
        *,
        user_id: int,
        session_id: int,
    ) -> list[BaseMessage]:
        del user_id, session_id
        return []


class ContextArchive:
    """在线请求只发布事件；MongoDB 写入由独立消费者完成。"""

    def __init__(
        self,
        *,
        redis: Redis,
        store: BaseStore,
        stream_name: str,
        rehydrate_turn_limit: int,
    ) -> None:
        self._redis = redis
        self._store = store
        self._stream_name = stream_name
        self._rehydrate_turn_limit = rehydrate_turn_limit

    async def publish_completed_turn(
        self,
        *,
        request_id: int,
        user_id: int,
        session_id: int,
        user_text: str,
        assistant_text: str,
    ) -> str:
        """发布一条可重试、可幂等落库的完成轮次事件。"""

        event = {
            "schema_version": 1,
            "request_id": request_id,
            "user_id": user_id,
            "session_id": session_id,
            "user_text": user_text,
            "assistant_text": assistant_text,
            "created_at": datetime.now(UTC).isoformat(),
        }
        # 不设置 MAXLEN：直接裁剪 Stream 可能删除仍在 Pending 中的消息体。
        try:
            return await self._redis.xadd(
                self._stream_name,
                {"event": json.dumps(event, ensure_ascii=False)},
            )
        except RedisError as exc:
            # API 只有在归档事件被 Redis 接收后才会发送 done。使用领域异常可以让
            # SSE 返回准确的 CONTEXT_ARCHIVE_UNAVAILABLE，而不是误报模型故障。
            raise ContextArchiveUnavailableError("Redis 归档通道暂时不可用") from exc

    async def load_messages(
        self,
        *,
        user_id: int,
        session_id: int,
    ) -> list[BaseMessage]:
        """Redis 热状态不存在时，从 Mongo 永久归档重建最近消息。"""

        try:
            # MongoDBStore 在没有语义查询时不承诺结果顺序。如果先传入 limit，可能截取到
            # 任意 N 条旧记录；冷恢复路径先取完整会话、排序后再保留最近 N 轮，保证语义正确。
            items = await self._store.asearch(
                conversation_namespace(user_id, session_id),
                limit=0,
            )
        except PyMongoError as exc:
            raise ContextStoreUnavailableError("MongoDB 上下文恢复失败") from exc
        # Store 搜索顺序不作为业务顺序；按归档时间与 requestId 显式排序。
        try:
            items.sort(
                key=lambda item: (
                    str(item.value["created_at"]),
                    int(item.value["request_id"]),
                )
            )
        except (KeyError, TypeError, ValueError) as exc:
            # 永久归档文档损坏时不能静默拼出错误上下文；显式失败更容易定位和修复数据。
            logger.exception("MongoDB 中存在无法恢复的上下文文档")
            raise ContextStoreUnavailableError("MongoDB 上下文文档格式无效") from exc

        items = items[-self._rehydrate_turn_limit :]

        messages: list[BaseMessage] = []
        for item in items:
            value = item.value
            request_id = int(value["request_id"])
            messages.extend(
                [
                    HumanMessage(
                        content=str(value["user_text"]),
                        id=f"request:{request_id}:user",
                    ),
                    AIMessage(
                        content=str(value["assistant_text"]),
                        id=f"request:{request_id}:assistant",
                    ),
                ]
            )
        return messages
