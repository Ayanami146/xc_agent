"""LangGraph 的线程状态与单次运行上下文。"""

from dataclasses import dataclass
from typing import Literal, TypedDict

from langgraph.graph import MessagesState


class RetrievedChunk(TypedDict):
    """写入本轮 LangGraph 状态的轻量检索结果。"""

    source_id: int
    document_id: str
    title: str
    content: str
    source_locator: str
    page: int | None
    score: float


class AgentState(MessagesState):
    """持久化对话消息，并覆盖保存本轮临时检索状态。

    MessagesState 已为 messages 字段配置 add_messages reducer：
    - 新 message id 会追加；
    - 相同 message id 会替换；
    - checkpointer 会按 thread_id 自动保存和恢复。

    其余字段没有 reducer，每轮 input_guard 都会重置，因此不会累积历史切片。
    """

    route: Literal["DIRECT", "RAG"] | None
    rewritten_query: str | None
    retrieved_chunks: list[RetrievedChunk]
    retrieval_degraded: bool


@dataclass(frozen=True, slots=True)
class AgentRunContext:
    """本次调用需要、但不应该写入长期 state 的运行参数。"""

    request_id: int
    user_id: int
    session_id: int
    system_prompt: str
    model_route: str
    max_output_tokens: int
    max_input_tokens: int
    # 下面两个 ID 只用于当前 LangGraph 运行中的消息定位。它们不能只由 requestId
    # 推导，因为开发环境重建 MySQL 后，自增 requestId 可能与 Redis 旧 checkpoint
    # 中的消息碰撞，add_messages reducer 会原位替换旧消息并破坏消息先后顺序。
    user_message_id: str
    assistant_message_id: str
