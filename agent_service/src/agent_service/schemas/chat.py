"""聊天接口的数据结构。

Python 内部统一使用 snake_case，序列化和反序列化时自动转换为 Java 常用的 camelCase。
"""

from datetime import UTC, datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """默认接受并输出 camelCase 的 Pydantic 基类。"""

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="forbid",
    )


class HistoryMessage(CamelModel):
    """兼容当前 Java DTO 的历史消息字段。

    最小骨架只接收该字段但不将其作为模型上下文。后续模型上下文必须从 Redis/MongoDB
    加载，防止误用 MySQL 用户可见消息破坏既定的数据边界。
    """

    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=8000)


class AgentPolicy(CamelModel):
    """由可信 Java 服务下发的本次运行策略。"""

    model_route: str = Field(min_length=1, max_length=100)
    knowledge_base_ids: list[str] = Field(min_length=1)
    tools_enabled: bool = False
    max_output_tokens: int = Field(default=1024, ge=1, le=32768)

    @field_validator("model_route")
    @classmethod
    def validate_model_route_not_blank(cls, value: str) -> str:
        """与 Java ``@NotBlank`` 契约保持一致。"""

        if not value.strip():
            raise ValueError("modelRoute 不能为空白字符串")
        return value.strip()


class ChatStreamRequest(CamelModel):
    """Java 调用智能体流式接口时的请求体。"""

    request_id: int = Field(gt=0)
    session_id: int = Field(gt=0)
    user_id: int = Field(gt=0)
    message: str = Field(min_length=1, max_length=1280000)
    # 当前 Java DTO 没有为 history 标注 @NotNull，因此同时兼容缺省、空数组和显式 null。
    # 该字段只是过渡契约，不会被传入模型；后续由 Redis/MongoDB 上下文加载节点替代。
    history: list[HistoryMessage] | None = None
    policy: AgentPolicy

    @field_validator("message")
    @classmethod
    def validate_message_not_blank(cls, value: str) -> str:
        """在建立 SSE 前拒绝只包含空白符的消息。"""

        if not value.strip():
            raise ValueError("message 不能为空白字符串")
        return value


class AgentEvent(BaseModel):
    """LangGraph 节点向 API 层发送的供应商无关事件。"""

    event: Literal["status", "delta", "citation", "usage"]
    payload: dict[str, Any]


class SseEnvelope(CamelModel):
    """内部 SSE 的统一 JSON 信封。"""

    event: Literal["meta", "status", "delta", "citation", "usage", "heartbeat", "done", "error"]
    request_id: int
    sequence: int = Field(gt=0)
    occurred_at: datetime
    payload: dict[str, Any]

    @classmethod
    def create(
        cls,
        *,
        event: str,
        request_id: int,
        sequence: int,
        payload: dict[str, Any],
    ) -> "SseEnvelope":
        """使用统一 UTC 时间创建事件，避免各节点自行处理时区。"""

        return cls(
            event=event,  # type: ignore[arg-type]
            request_id=request_id,
            sequence=sequence,
            occurred_at=datetime.now(UTC),
            payload=payload,
        )


class CancelResponse(CamelModel):
    """取消接口的幂等响应。"""

    request_id: int
    cancel_requested: bool
    status: Literal["CANCEL_REQUESTED", "NOT_RUNNING"]
