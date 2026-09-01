"""LangGraph 运行协调器。"""

import asyncio
import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Any
from uuid import uuid4

from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.graph.state import CompiledStateGraph
from redis.asyncio import Redis
from redis.exceptions import RedisError

from agent_service.core.cancellation import CancellationRegistry
from agent_service.core.exceptions import (
    AgentCancelledError,
    ContextStoreUnavailableError,
    RunAlreadyActiveError,
)
from agent_service.graph.state import AgentRunContext
from agent_service.graph.workflow import build_agent_graph
from agent_service.models.gateway import ModelGateway
from agent_service.schemas.chat import AgentEvent, ChatStreamRequest
from agent_service.schemas.role import RoleProfile
from agent_service.services.context_archive import ContextArchiveBackend
from agent_service.services.manual_rag import ManualRagService
from agent_service.services.role_profile import RoleProfileProvider

logger = logging.getLogger(__name__)


class AgentRuntime:
    """协调 Redis checkpointer、LangGraph 流和 Mongo 异步归档。"""

    def __init__(
        self,
        model_gateway: ModelGateway,
        role_profile_provider: RoleProfileProvider,
        *,
        checkpointer: BaseCheckpointSaver,
        archive: ContextArchiveBackend,
        redis: Redis | None,
        model_max_input_tokens: int,
        session_lock_timeout_seconds: int,
        rag_service: ManualRagService | None = None,
        cancellation_registry: CancellationRegistry | None = None,
    ) -> None:
        self.model_gateway = model_gateway
        self.role_profile_provider = role_profile_provider
        self.cancellation_registry = cancellation_registry or CancellationRegistry()
        self.checkpointer = checkpointer
        self.archive = archive
        self.redis = redis
        self.model_max_input_tokens = model_max_input_tokens
        self.session_lock_timeout_seconds = session_lock_timeout_seconds
        self.rag_service = rag_service
        self._local_session_locks: dict[str, asyncio.Lock] = {}
        self._local_session_locks_guard = asyncio.Lock()
        self.graph: CompiledStateGraph = build_agent_graph(
            model_gateway,
            self.cancellation_registry,
            checkpointer,
            rag_service,
        )

    def load_active_role(self) -> RoleProfile:
        return self.role_profile_provider.load()

    @staticmethod
    def _thread_id(user_id: int, session_id: int) -> str:
        """同一 sessionId 在不同用户下不能共享 checkpoint。"""

        return f"user:{user_id}:session:{session_id}"

    async def _rehydrate_if_needed(
        self,
        *,
        config: dict[str, Any],
        user_id: int,
        session_id: int,
    ) -> None:
        """仅在 Redis checkpointer 未命中时查询 MongoDBStore。"""

        if await self.checkpointer.aget_tuple(config) is not None:
            return
        messages = await self.archive.load_messages(
            user_id=user_id,
            session_id=session_id,
        )
        if messages:
            # 让 LangGraph 自己生成合法 checkpoint；不拼 Redis key、不写内部格式。
            await self.graph.aupdate_state(config, {"messages": messages})

    @asynccontextmanager
    async def _session_lock(self, thread_id: str) -> AsyncIterator[None]:
        """以非阻塞方式串行化同一会话。

        持久化模式使用 Redis 分布式锁；关闭持久化的测试模式使用进程内锁。两种实现都
        保持“第二个并发请求立即失败”的语义，避免测试路径和生产路径行为不一致。
        """

        if self.redis is not None:
            lock = self.redis.lock(
                f"xc:agent:context:lock:v1:{thread_id}",
                timeout=self.session_lock_timeout_seconds,
                blocking_timeout=0,
            )
            try:
                acquired = await lock.acquire(blocking=False)
            except RedisError as exc:
                raise ContextStoreUnavailableError("Redis 会话锁暂时不可用") from exc
            if not acquired:
                raise RunAlreadyActiveError(f"threadId={thread_id} 已有运行")

            try:
                yield
            finally:
                try:
                    # 锁可能因请求超时而自动过期；仅在当前进程仍是所有者时释放。
                    if await lock.owned():
                        await lock.release()
                except RedisError:
                    # 清理失败不能覆盖模型或归档阶段的原始异常，但必须留下完整日志。
                    logger.exception("释放 Redis 会话锁失败，threadId=%s", thread_id)
            return

        async with self._local_session_locks_guard:
            local_lock = self._local_session_locks.setdefault(thread_id, asyncio.Lock())
            if local_lock.locked():
                raise RunAlreadyActiveError(f"threadId={thread_id} 已有运行")
            await local_lock.acquire()

        try:
            yield
        finally:
            local_lock.release()
            async with self._local_session_locks_guard:
                # 如果释放后已有新请求取得同一把锁，就不能从字典删除它。
                if not local_lock.locked():
                    self._local_session_locks.pop(thread_id, None)

    async def stream(
        self,
        request: ChatStreamRequest,
        role_profile: RoleProfile,
    ) -> AsyncIterator[AgentEvent]:
        """执行一轮，并在返回前确认归档事件已经进入 Redis Stream。"""

        thread_id = self._thread_id(request.user_id, request.session_id)
        config = {"configurable": {"thread_id": thread_id}}
        async with self._session_lock(thread_id):
            registered = False
            final_state: dict[str, Any] | None = None
            try:
                # register() 可能因另一个会话使用了相同 requestId 而失败。只有注册成功后
                # 才能在 finally 中 finish，避免误删原运行的取消令牌。
                await self.cancellation_registry.register(request.request_id)
                registered = True

                await self._rehydrate_if_needed(
                    config=config,
                    user_id=request.user_id,
                    session_id=request.session_id,
                )

                # requestId 是 Java/MySQL 的业务主键，正常情况下会递增；但开发环境经常
                # 只重建 MySQL 而保留 Redis checkpoint，此时新 requestId 可能与旧消息
                # 重复。LangGraph 会按消息 id 原位替换，导致旧 AIMessage 仍位于列表末尾，
                # input_guard 因而误判当前输入不是 HumanMessage。为每次真实运行生成独立
                # 消息前缀即可消除这种存储生命周期差异，同时不改变任何对外协议字段。
                message_prefix = f"request:{request.request_id}:run:{uuid4().hex}"
                run_context = AgentRunContext(
                    request_id=request.request_id,
                    user_id=request.user_id,
                    session_id=request.session_id,
                    system_prompt=role_profile.system_prompt,
                    model_route=request.policy.model_route,
                    max_output_tokens=request.policy.max_output_tokens,
                    max_input_tokens=self.model_max_input_tokens,
                    user_message_id=f"{message_prefix}:user",
                    assistant_message_id=f"{message_prefix}:assistant",
                )
                input_message = HumanMessage(
                    content=request.message,
                    id=run_context.user_message_id,
                )

                async for part in self.graph.astream(
                    {"messages": [input_message]},
                    config,
                    context=run_context,
                    stream_mode=["custom", "values"],
                    version="v2",
                ):
                    if await self.cancellation_registry.is_cancelled(request.request_id):
                        raise AgentCancelledError(f"requestId={request.request_id} 已取消")
                    if part["type"] == "custom":
                        yield AgentEvent.model_validate(part["data"])
                    elif part["type"] == "values":
                        final_state = part["data"]

                if final_state is None:
                    raise RuntimeError("LangGraph 未返回最终状态")
                last = final_state["messages"][-1]
                if not isinstance(last, AIMessage):
                    raise RuntimeError("LangGraph 最终状态缺少 AIMessage")

                # LangGraph 流结束时 checkpoint 已由 saver 写入；随后投递归档事件。
                # API 路由只有在本方法结束后才发送 done，不会把未入队轮次报告为成功。
                await self.archive.publish_completed_turn(
                    request_id=request.request_id,
                    user_id=request.user_id,
                    session_id=request.session_id,
                    user_text=request.message,
                    assistant_text=str(last.content),
                )
            finally:
                if registered:
                    await self.cancellation_registry.finish(request.request_id)

    async def cancel(self, request_id: int) -> bool:
        return await self.cancellation_registry.cancel(request_id)
