"""LangGraph 上下文节点参考实现。

参考实现，尚未自动接入当前项目。节点只协调状态和服务，不在函数内部创建 Redis、MongoDB
或模型客户端；正式接入时应继续沿用当前 AgentRuntime 的依赖注入方式。
"""

from collections.abc import Sequence
from typing import Protocol, TypedDict

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage
from langchain_core.messages.utils import count_tokens_approximately
from langgraph.types import StreamWriter

from context_models import ContextEntry, ContextSnapshot, ContextTurn
from context_repository import ContextRepository


class ContextCompressor(Protocol):
    """模型压缩器协议，测试可以注入确定性实现。"""

    async def compress(
        self,
        snapshot: ContextSnapshot,
        *,
        recent_turns: int,
        summary_max_tokens: int,
    ) -> ContextSnapshot:
        """生成新摘要、事实和保留的近期上下文。"""


class ContextPolicy(TypedDict):
    """本轮上下文预算。"""

    soft_token_limit: int
    hard_token_limit: int
    recent_turns: int
    summary_max_tokens: int
    chars_per_token: float


class AgentState(TypedDict, total=False):
    """示例所需的 LangGraph 状态字段。"""

    request_id: int
    session_id: int
    user_id: int
    message: str
    system_prompt: str
    final_answer: str
    model_name: str
    context_policy: ContextPolicy
    context_snapshot: ContextSnapshot
    context_revision: int
    model_messages: list[BaseMessage]


def _assemble_messages(state: AgentState, snapshot: ContextSnapshot) -> list[BaseMessage]:
    """按固定顺序装配模型消息，禁止读取请求 history 或 MySQL 消息。"""

    messages: list[BaseMessage] = [SystemMessage(content=state["system_prompt"])]
    if snapshot.summary:
        messages.append(SystemMessage(content=f"会话摘要：\n{snapshot.summary}"))
    if snapshot.facts:
        facts_text = "\n".join(f"- {fact.key}: {fact.value}" for fact in snapshot.facts)
        messages.append(SystemMessage(content=f"已确认事实：\n{facts_text}"))
    for entry in snapshot.recent_entries:
        message_type = HumanMessage if entry.role == "user" else AIMessage
        messages.append(message_type(content=entry.content))
    messages.append(HumanMessage(content=state["message"]))
    return messages


async def load_context(
    state: AgentState,
    writer: StreamWriter,
    repository: ContextRepository,
) -> dict[str, object]:
    """加载 Redis/MongoDB 双层上下文，并记录进入本轮时的 revision。"""

    writer({"event": "status", "payload": {"stage": "context", "message": "正在加载上下文"}})
    snapshot = await repository.load(state["user_id"], state["session_id"])
    return {
        "context_snapshot": snapshot,
        "context_revision": snapshot.revision,
        "model_messages": _assemble_messages(state, snapshot),
    }


async def compact_context(
    state: AgentState,
    writer: StreamWriter,
    compressor: ContextCompressor,
) -> dict[str, object]:
    """达到软阈值时同步压缩，并确保最终消息不超过硬预算。"""

    policy = state["context_policy"]
    messages: Sequence[BaseMessage] = state["model_messages"]
    estimated = count_tokens_approximately(
        messages,
        chars_per_token=policy["chars_per_token"],
    )
    if estimated <= policy["soft_token_limit"]:
        return {}

    writer({"event": "status", "payload": {"stage": "context", "message": "正在压缩上下文"}})
    compressed = await compressor.compress(
        state["context_snapshot"],
        recent_turns=policy["recent_turns"],
        summary_max_tokens=policy["summary_max_tokens"],
    )
    rebuilt = _assemble_messages(state, compressed)
    rebuilt_tokens = count_tokens_approximately(
        rebuilt,
        chars_per_token=policy["chars_per_token"],
    )
    if rebuilt_tokens > policy["hard_token_limit"]:
        # 正式实现应调用确定性裁剪器，只从最旧的近期条目开始删除，不删除角色提示和事实。
        raise ValueError("压缩后上下文仍超过硬预算")
    return {"context_snapshot": compressed, "model_messages": rebuilt}


async def persist_context(
    state: AgentState,
    writer: StreamWriter,
    repository: ContextRepository,
) -> dict[str, object]:
    """仅在回答通过输出校验后，把本轮上下文写入 MongoDB 和 Redis。"""

    writer({"event": "status", "payload": {"stage": "context", "message": "正在保存上下文"}})
    snapshot = state["context_snapshot"]
    turn = ContextTurn(
        request_id=state["request_id"],
        user_id=state["user_id"],
        session_id=state["session_id"],
        user_context=state["message"].strip(),
        assistant_context=state["final_answer"].strip(),
        model_name=state["model_name"],
    )
    next_snapshot = snapshot.model_copy(
        update={
            "recent_entries": [
                *snapshot.recent_entries,
                ContextEntry(
                    request_id=state["request_id"],
                    role="user",
                    content=turn.user_context,
                ),
                ContextEntry(
                    request_id=state["request_id"],
                    role="assistant",
                    content=turn.assistant_context,
                ),
            ]
        }
    )
    persisted = await repository.save(
        next_snapshot,
        turn,
        expected_revision=state["context_revision"],
    )
    return {"context_snapshot": persisted, "context_revision": persisted.revision}
