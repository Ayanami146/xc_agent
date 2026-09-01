"""Redis 热缓存 + MongoDB 持久化仓储参考实现。

参考实现，尚未自动接入当前项目。此文件重点展示依赖方向、降级策略、幂等和乐观锁，
实际接入时应拆入 agent_service 的 infrastructure/services 目录，并由 FastAPI lifespan
统一创建和关闭 Redis、MongoDB 客户端。
"""

import logging
from dataclasses import dataclass

from pydantic import ValidationError
from pymongo import AsyncMongoClient
from pymongo.errors import DuplicateKeyError, PyMongoError
from redis.asyncio import Redis
from redis.exceptions import RedisError

from context_models import ContextSnapshot, ContextTurn

logger = logging.getLogger(__name__)


class ContextStoreUnavailableError(RuntimeError):
    """MongoDB 真相源无法完成必要读写。"""


class ContextRevisionConflictError(RuntimeError):
    """上下文快照在本轮运行期间已被其他请求更新。"""


@dataclass(frozen=True, slots=True)
class ContextRepositorySettings:
    """仓储需要的最小配置集合。"""

    mongodb_database: str = "xinchuang_agent_context"
    redis_key_prefix: str = "xc:agent:context:v1:"
    redis_ttl_seconds: int = 604800


class ContextRepository:
    """以 MongoDB 为真相源、Redis 为可降级热缓存的上下文仓储。"""

    def __init__(
        self,
        redis_client: Redis,
        mongo_client: AsyncMongoClient,
        settings: ContextRepositorySettings,
    ) -> None:
        self._redis = redis_client
        self._database = mongo_client[settings.mongodb_database]
        self._sessions = self._database["context_sessions"]
        self._turns = self._database["context_turns"]
        self._settings = settings

    def _redis_key(self, user_id: int, session_id: int) -> str:
        """生成带版本号和用户边界的 Redis Key。"""

        return f"{self._settings.redis_key_prefix}session:{user_id}:{session_id}"

    async def load(self, user_id: int, session_id: int) -> ContextSnapshot:
        """优先读取 Redis，失败或未命中时回源 MongoDB。"""

        key = self._redis_key(user_id, session_id)
        try:
            cached = await self._redis.get(key)
            if cached:
                snapshot = ContextSnapshot.model_validate_json(cached)
                # 成功访问后刷新滑动 TTL，让活跃会话继续停留在热缓存。
                await self._redis.expire(key, self._settings.redis_ttl_seconds)
                return snapshot
        except (RedisError, ValidationError):
            # Redis 是缓存，故障和损坏不阻断聊天；日志不能记录上下文正文。
            logger.warning("Redis 上下文读取失败，回源 MongoDB", exc_info=True)

        try:
            document = await self._sessions.find_one(
                {"userId": user_id, "sessionId": session_id}
            )
        except PyMongoError as exc:
            raise ContextStoreUnavailableError("MongoDB 上下文读取失败") from exc

        if document is None:
            snapshot = ContextSnapshot(user_id=user_id, session_id=session_id)
        else:
            document.pop("_id", None)
            snapshot = ContextSnapshot.model_validate(document)

        # 回填 Redis 失败不影响已经从 MongoDB 得到的结果。
        await self._cache_best_effort(snapshot)
        return snapshot

    async def save(
        self,
        snapshot: ContextSnapshot,
        turn: ContextTurn,
        *,
        expected_revision: int,
    ) -> ContextSnapshot:
        """先持久化 MongoDB，再刷新 Redis。

        context_sessions 使用 revision 比较并交换，避免两个请求静默互相覆盖；requestId
        同时保存在 lastRequestId 和 context_turns 唯一索引中，以便调用方安全重试。
        """

        next_snapshot = snapshot.model_copy(
            update={
                "revision": expected_revision + 1,
                "last_request_id": turn.request_id,
            }
        )
        replacement = next_snapshot.model_dump(by_alias=True, mode="python")

        try:
            current = await self._sessions.find_one(
                {"userId": snapshot.user_id, "sessionId": snapshot.session_id}
            )
            if current and current.get("lastRequestId") == turn.request_id:
                # 同一 requestId 已成功落库，直接返回数据库中的最终状态。
                current.pop("_id", None)
                persisted = ContextSnapshot.model_validate(current)
                await self._cache_best_effort(persisted)
                return persisted

            result = await self._sessions.replace_one(
                {
                    "userId": snapshot.user_id,
                    "sessionId": snapshot.session_id,
                    "revision": expected_revision,
                },
                replacement,
                upsert=expected_revision == 0,
            )
            if result.matched_count == 0 and result.upserted_id is None:
                raise ContextRevisionConflictError("上下文 revision 已变化")

            # context_turns 用于重建和诊断；重复 requestId 视为幂等成功。
            try:
                await self._turns.insert_one(turn.model_dump(by_alias=True, mode="python"))
            except DuplicateKeyError:
                pass
        except ContextRevisionConflictError:
            raise
        except DuplicateKeyError as exc:
            # 首次 upsert 与其他请求竞争唯一索引时，统一转换为 revision 冲突。
            raise ContextRevisionConflictError("上下文首次创建发生并发冲突") from exc
        except PyMongoError as exc:
            raise ContextStoreUnavailableError("MongoDB 上下文写入失败") from exc

        await self._cache_best_effort(next_snapshot)
        return next_snapshot

    async def clear(self, user_id: int, session_id: int) -> tuple[int, int, bool]:
        """幂等清理 MongoDB 快照、轮次和 Redis 热缓存。"""

        try:
            session_result = await self._sessions.delete_one(
                {"userId": user_id, "sessionId": session_id}
            )
            turn_result = await self._turns.delete_many(
                {"userId": user_id, "sessionId": session_id}
            )
        except PyMongoError as exc:
            raise ContextStoreUnavailableError("MongoDB 上下文清理失败") from exc

        redis_deleted = False
        try:
            redis_deleted = bool(await self._redis.delete(self._redis_key(user_id, session_id)))
        except RedisError:
            logger.warning("MongoDB 已清理，但 Redis Key 删除失败", exc_info=True)

        return session_result.deleted_count, turn_result.deleted_count, redis_deleted

    async def _cache_best_effort(self, snapshot: ContextSnapshot) -> None:
        """以 SET + EX 原子刷新 Redis；缓存失败不向上抛出。"""

        try:
            await self._redis.set(
                self._redis_key(snapshot.user_id, snapshot.session_id),
                snapshot.model_dump_json(by_alias=True),
                ex=self._settings.redis_ttl_seconds,
            )
        except RedisError:
            logger.warning("Redis 上下文回填失败，继续使用 MongoDB", exc_info=True)
