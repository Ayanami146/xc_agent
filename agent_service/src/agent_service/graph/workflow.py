"""基于 LangGraph 的条件 RAG 智能体工作流。"""

import logging
from collections.abc import Callable
from typing import Any

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
    trim_messages,
)
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph
from langgraph.runtime import Runtime

from agent_service.core.cancellation import CancellationRegistry
from agent_service.core.exceptions import (
    AgentCancelledError,
    ContextTooLargeError,
    EmptyModelOutputError,
    RagUnavailableError,
)
from agent_service.graph.state import AgentRunContext, AgentState, RetrievedChunk
from agent_service.models.gateway import ModelGateway
from agent_service.services.manual_rag import ManualRagService

logger = logging.getLogger(__name__)

ROUTE_PROMPT = """你是客服请求路由器。请结合对话上下文判断当前问题是否需要查询维修手册。
设备、操作系统、软件故障、驱动、维修步骤、保修政策和手册事实选择 RAG；
问候、闲聊、角色说明和不需要企业知识的普通对话选择 DIRECT。
只输出 DIRECT 或 RAG，不要解释。"""

REWRITE_PROMPT = """你是维修手册检索查询改写器。请结合对话上下文，把最后一个用户问题
改写为一条上下文完整、可独立用于语义检索的中文查询。保留产品、型号、系统版本和故障现象，
不要回答问题，只输出改写后的查询。"""


def build_agent_graph(
    model_gateway: ModelGateway,
    cancellation_registry: CancellationRegistry,
    checkpointer: BaseCheckpointSaver,
    rag_service: ManualRagService | None = None,
) -> CompiledStateGraph:
    """创建“语义路由—按需 RAG—流式生成”图，并交给 LangGraph 持久化消息。"""

    def auxiliary_messages(
        prompt: str,
        state: AgentState,
        context: AgentRunContext,
    ) -> list[BaseMessage]:
        """按模型输入预算裁剪路由和改写节点看到的对话。"""

        return trim_messages(
            [SystemMessage(content=prompt), *state["messages"]],
            max_tokens=context.max_input_tokens,
            token_counter="approximate",
            strategy="last",
            start_on="human",
            include_system=True,
            allow_partial=False,
        )

    async def input_guard(
        state: AgentState,
        runtime: Runtime[AgentRunContext],
    ) -> dict[str, Any]:
        """规范化本轮用户消息，并清空上一轮覆盖保存的 RAG 临时状态。"""

        runtime.stream_writer(
            {
                "event": "status",
                "payload": {"stage": "safety", "message": "正在检查输入内容"},
            }
        )
        last = state["messages"][-1]
        if not isinstance(last, HumanMessage):
            raise ValueError("输入状态的最后一条消息必须是 HumanMessage")

        normalized = " ".join(str(last.content).split())
        return {
            "messages": [HumanMessage(content=normalized, id=last.id)],
            "route": None,
            "rewritten_query": None,
            "retrieved_chunks": [],
            "retrieval_degraded": False,
        }

    async def route_query(
        state: AgentState,
        runtime: Runtime[AgentRunContext],
    ) -> dict[str, str]:
        """使用模型判断本轮是否需要维修手册；RAG 关闭时直接走普通生成。"""

        runtime.stream_writer(
            {
                "event": "status",
                "payload": {"stage": "intent", "message": "正在判断问题类型"},
            }
        )
        if rag_service is None:
            return {"route": "DIRECT"}

        try:
            decision = await model_gateway.complete(
                auxiliary_messages(ROUTE_PROMPT, state, runtime.context),
                max_output_tokens=16,
            )
        except Exception:
            # 路由是辅助判断；单次非流式调用失败时仍可继续检索，不能让本轮提前中断。
            logger.warning("语义路由调用失败，按 RAG 分支继续", exc_info=True)
            return {"route": "RAG"}
        # 模型若没有严格遵守二选一格式，默认检索，避免手册事实被直接臆测。
        return {"route": "DIRECT" if decision.strip().upper() == "DIRECT" else "RAG"}

    async def rewrite_query(
        state: AgentState,
        runtime: Runtime[AgentRunContext],
    ) -> dict[str, str]:
        """仅为 RAG 分支把多轮追问改写为独立检索查询。"""

        runtime.stream_writer(
            {
                "event": "status",
                "payload": {"stage": "intent", "message": "正在整理检索问题"},
            }
        )
        original = str(state["messages"][-1].content)
        try:
            rewritten = await model_gateway.complete(
                auxiliary_messages(REWRITE_PROMPT, state, runtime.context),
                max_output_tokens=256,
            )
        except Exception:
            # 查询改写不是回答所必需的依赖，失败后以原问题检索即可。
            logger.warning("RAG 查询重写失败，回退到原始问题", exc_info=True)
            rewritten = ""
        return {"rewritten_query": rewritten.strip() or original}

    async def sync_manual_index(
        state: AgentState,
        runtime: Runtime[AgentRunContext],
    ) -> dict[str, bool]:
        """在检索前按 Java 发布清单同步 Chroma；失败时禁止使用陈旧索引。"""

        del state
        runtime.stream_writer(
            {
                "event": "status",
                "payload": {"stage": "retrieval", "message": "正在同步维修手册索引"},
            }
        )
        assert rag_service is not None
        try:
            await rag_service.sync_index()
            return {"retrieval_degraded": False}
        except RagUnavailableError as exc:
            # 将底层清单、解析、Embedding 或 Chroma 异常保留在服务日志中，
            # 前端仍只接收稳定的降级提示，避免再次出现只能看到“知识库不可用”
            # 却无法判断实际失败环节的情况。
            logger.warning(
                "维修手册索引同步失败，本轮降级为无检索回答：%s",
                exc,
                exc_info=True,
            )
            runtime.stream_writer(
                {
                    "event": "status",
                    "payload": {
                        "stage": "retrieval",
                        "message": "知识库暂时不可用，将基于通用能力回答",
                    },
                }
            )
            return {"retrieval_degraded": True}

    async def retrieve(
        state: AgentState,
        runtime: Runtime[AgentRunContext],
    ) -> dict[str, Any]:
        """使用重写后的查询执行稠密检索，并立即发送可落库的引用快照。"""

        if state.get("retrieval_degraded"):
            return {"retrieved_chunks": []}
        runtime.stream_writer(
            {
                "event": "status",
                "payload": {"stage": "retrieval", "message": "正在检索维修手册"},
            }
        )
        assert rag_service is not None
        query = state.get("rewritten_query") or str(state["messages"][-1].content)
        try:
            matches = await rag_service.search(query)
        except RagUnavailableError as exc:
            logger.warning(
                "维修手册向量检索失败，本轮降级为无检索回答：%s",
                exc,
                exc_info=True,
            )
            runtime.stream_writer(
                {
                    "event": "status",
                    "payload": {
                        "stage": "retrieval",
                        "message": "知识库暂时不可用，将基于通用能力回答",
                    },
                }
            )
            return {"retrieved_chunks": [], "retrieval_degraded": True}

        chunks: list[RetrievedChunk] = [
            {
                "source_id": match.source_id,
                "document_id": match.document_id,
                "title": match.title,
                "content": match.content,
                "source_locator": match.source_locator,
                "page": match.page,
                "score": match.score,
            }
            for match in matches
        ]
        if chunks:
            runtime.stream_writer(
                {
                    "event": "citation",
                    "payload": {
                        "sources": [
                            {
                                "sourceId": chunk["source_id"],
                                "title": chunk["title"],
                                "snippet": chunk["content"][:240],
                                "sourceLocator": chunk["source_locator"],
                                "page": chunk["page"],
                                "score": round(chunk["score"], 6),
                            }
                            for chunk in chunks
                        ]
                    },
                }
            )
        return {"retrieved_chunks": chunks}

    def generation_system_prompt(state: AgentState, context: AgentRunContext) -> str:
        """组合角色提示词和本轮 RAG 证据，不修改 Redis 中的历史消息。"""

        if state.get("route") != "RAG":
            return context.system_prompt
        chunks = state.get("retrieved_chunks") or []
        if chunks:
            source_parts: list[str] = []
            for index, chunk in enumerate(chunks, start=1):
                page_text = f"（第{chunk['page']}页）" if chunk["page"] else ""
                source_parts.append(
                    f"[来源{index}] {chunk['title']}{page_text}\n{chunk['content']}"
                )
            rag_instruction = (
                "以下是维修手册检索结果。涉及手册事实时只能依据这些内容回答；"
                "若证据不足必须明确说明，不得编造来源。\n\n" + "\n\n".join(source_parts)
            )
        elif state.get("retrieval_degraded"):
            rag_instruction = (
                "本轮维修手册知识库暂时不可用。可以给出通用排查建议，但必须明确说明"
                "未能核对维修手册，不得声称存在引用。"
            )
        else:
            rag_instruction = (
                "没有检索到匹配的维修手册。可以给出通用排查建议，但应说明未找到匹配手册，"
                "不得编造企业知识来源。"
            )
        return f"{context.system_prompt}\n\n{rag_instruction}"

    async def generate(
        state: AgentState,
        runtime: Runtime[AgentRunContext],
    ) -> dict[str, list[AIMessage]]:
        """裁剪模型输入，并按增量文本生成最终 AIMessage。"""

        context = runtime.context
        runtime.stream_writer(
            {
                "event": "status",
                "payload": {"stage": "generation", "message": "正在生成回答"},
            }
        )

        model_messages = trim_messages(
            [
                SystemMessage(content=generation_system_prompt(state, context)),
                *state["messages"],
            ],
            max_tokens=context.max_input_tokens,
            token_counter="approximate",
            strategy="last",
            start_on="human",
            include_system=True,
            allow_partial=False,
        )
        if not any(message.id == context.user_message_id for message in model_messages):
            raise ContextTooLargeError("本轮用户消息超过模型输入上限")

        parts: list[str] = []
        async for text in model_gateway.stream(
            model_messages,
            max_output_tokens=context.max_output_tokens,
        ):
            if await cancellation_registry.is_cancelled(context.request_id):
                raise AgentCancelledError(f"requestId={context.request_id} 已取消")
            parts.append(text)
            runtime.stream_writer({"event": "delta", "payload": {"content": text}})

        answer = "".join(parts).strip()
        if not answer:
            raise EmptyModelOutputError("模型没有生成可展示内容")
        return {
            "messages": [AIMessage(content=answer, id=context.assistant_message_id)],
        }

    async def output_validate(state: AgentState) -> dict:
        """确认图的最后一条消息是非空回答。"""

        last = state["messages"][-1]
        if not isinstance(last, AIMessage) or not str(last.content).strip():
            raise EmptyModelOutputError("回答校验失败：内容为空")
        return {}

    graph = StateGraph(AgentState, context_schema=AgentRunContext)
    graph.add_node("input_guard", input_guard)
    graph.add_node("route_query", route_query)
    graph.add_node("rewrite_query", rewrite_query)
    graph.add_node("sync_manual_index", sync_manual_index)
    graph.add_node("retrieve", retrieve)
    graph.add_node("generate", generate)
    graph.add_node("output_validate", output_validate)
    graph.add_edge(START, "input_guard")
    graph.add_edge("input_guard", "route_query")
    graph.add_conditional_edges(
        "route_query",
        lambda state: state.get("route", "RAG"),
        {"DIRECT": "generate", "RAG": "rewrite_query"},
    )
    graph.add_edge("rewrite_query", "sync_manual_index")
    graph.add_edge("sync_manual_index", "retrieve")
    graph.add_edge("retrieve", "generate")
    graph.add_edge("generate", "output_validate")
    graph.add_edge("output_validate", END)
    return graph.compile(checkpointer=checkpointer)


AgentGraphFactory = Callable[
    [ModelGateway, CancellationRegistry, BaseCheckpointSaver, ManualRagService | None],
    CompiledStateGraph,
]
