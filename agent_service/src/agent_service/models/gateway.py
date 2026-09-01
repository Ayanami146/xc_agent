"""模型流式调用适配器。

LangGraph 只依赖 ``ModelGateway`` 协议，因此以后切换私有化模型、国产模型或其他供应商时，
只需增加新的 Gateway，不需要修改工作流节点。
"""

import asyncio
from collections.abc import AsyncIterator, Sequence
from typing import Any, Protocol

from langchain_core.messages import BaseMessage
from langchain_openai import ChatOpenAI

from agent_service.config import Settings


class ModelGateway(Protocol):
    """智能体工作流需要的最小模型能力。"""

    @property
    def model_name(self) -> str:
        """返回实际模型标识，用于日志、usage 和诊断。"""

    def stream(
        self,
        messages: Sequence[BaseMessage],
        *,
        max_output_tokens: int,
    ) -> AsyncIterator[str]:
        """按增量文本异步输出模型结果。"""

    async def complete(
        self,
        messages: Sequence[BaseMessage],
        *,
        max_output_tokens: int,
    ) -> str:
        """执行一次非流式文本调用，供路由和查询重写节点使用。"""


class MockModelGateway:
    """无需外部网络和密钥的开发模型。"""

    @property
    def model_name(self) -> str:
        return "mock-model"

    async def stream(
        self,
        messages: Sequence[BaseMessage],
        *,
        max_output_tokens: int,
    ) -> AsyncIterator[str]:
        """将固定说明按小块输出，以真实模拟 token 流式到达。"""

        del max_output_tokens  # Mock 不计 token，但保留同一接口以便无缝替换。
        user_text = str(messages[-1].content) if messages else ""
        answer = (
            f"已收到您的问题：{user_text}\n"
            "当前运行的是本地 Mock 模型；上下文链路可正常验证，但不会调用外部大模型。"
        )
        for start in range(0, len(answer), 8):
            # 主动让出事件循环，测试异步流和取消路径，而不是一次返回完整字符串。
            await asyncio.sleep(0)
            yield answer[start : start + 8]

    async def complete(
        self,
        messages: Sequence[BaseMessage],
        *,
        max_output_tokens: int,
    ) -> str:
        """为离线开发提供可预测的路由和查询重写结果。"""

        del max_output_tokens
        system_text = str(messages[0].content) if messages else ""
        user_text = str(messages[-1].content) if messages else ""
        if "只输出 DIRECT 或 RAG" in system_text:
            rag_terms = ("故障", "维修", "手册", "驱动", "系统", "保修", "电脑", "打印机")
            return "RAG" if any(term in user_text for term in rag_terms) else "DIRECT"
        return user_text


class OpenAIModelGateway:
    """基于 LangChain ``ChatOpenAI`` 的真实模型适配器。"""

    def __init__(self, settings: Settings) -> None:
        api_key = settings.openai_api_key
        if api_key is None:
            # Settings 已经做过校验；这里保留防御式检查，避免未来绕过配置工厂。
            raise ValueError("缺少 OpenAI API Key")

        self._model_name = settings.model_name
        self._model = ChatOpenAI(
            model=settings.model_name,
            api_key=api_key.get_secret_value(),
            base_url=settings.openai_base_url,
            timeout=settings.model_timeout_seconds,
            streaming=True,
        )

    @property
    def model_name(self) -> str:
        return self._model_name

    async def stream(
        self,
        messages: Sequence[BaseMessage],
        *,
        max_output_tokens: int,
    ) -> AsyncIterator[str]:
        """流式调用模型，并把不同内容块统一转换成纯文本。"""

        async for chunk in self._model.astream(messages, max_tokens=max_output_tokens):
            text = _extract_text(chunk.content)
            if text:
                yield text

    async def complete(
        self,
        messages: Sequence[BaseMessage],
        *,
        max_output_tokens: int,
    ) -> str:
        """复用同一个 ChatOpenAI 客户端完成路由和查询重写，不引入第二套模型 SDK。"""

        response = await self._model.ainvoke(messages, max_tokens=max_output_tokens)
        return _extract_text(response.content).strip()


def _extract_text(content: Any) -> str:
    """兼容 LangChain 的字符串内容和结构化内容块。"""

    if isinstance(content, str):
        return content
    if isinstance(content, list):
        pieces: list[str] = []
        for item in content:
            if isinstance(item, str):
                pieces.append(item)
            elif isinstance(item, dict) and isinstance(item.get("text"), str):
                pieces.append(item["text"])
        return "".join(pieces)
    return ""


def create_model_gateway(settings: Settings) -> ModelGateway:
    """依据配置创建模型适配器。"""

    if settings.model_provider == "mock":
        return MockModelGateway()
    return OpenAIModelGateway(settings)
