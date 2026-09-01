"""Agent 上下文数据模型参考实现。

参考实现，尚未自动接入当前项目。Python 内部使用 snake_case，写入 Redis/MongoDB 时
通过 Pydantic alias 输出 camelCase，方便与 Java 约定和数据库文档保持一致。
"""

from datetime import UTC, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class ContextModel(BaseModel):
    """上下文模型公共配置。"""

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="forbid",
    )


class ContextFact(ContextModel):
    """模型可复用的重要事实，而不是面向用户展示的聊天消息。"""

    key: str = Field(min_length=1, max_length=100)
    value: str = Field(min_length=1, max_length=4000)
    source_request_id: int = Field(gt=0)
    confidence: float = Field(default=1.0, ge=0.0, le=1.0)
    updated_at: datetime = Field(default_factory=lambda: datetime.now(UTC))


class ContextEntry(ContextModel):
    """近期模型上下文条目。

    content 可以来自用户问题或 Agent 最终回答的规范化结果，但它属于独立的模型上下文，
    不从 MySQL chat_message 反向读取，也不承诺与页面展示文本完全相同。
    """

    request_id: int = Field(gt=0)
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=128000)
    estimated_tokens: int = Field(default=0, ge=0)
    created_at: datetime = Field(default_factory=lambda: datetime.now(UTC))


class ContextSnapshot(ContextModel):
    """一个用户会话当前可直接装配给模型的上下文快照。"""

    schema_version: int = Field(default=1, ge=1)
    user_id: int = Field(gt=0)
    session_id: int = Field(gt=0)
    revision: int = Field(default=0, ge=0)
    summary: str = Field(default="", max_length=32000)
    facts: list[ContextFact] = Field(default_factory=list)
    recent_entries: list[ContextEntry] = Field(default_factory=list)
    estimated_tokens: int = Field(default=0, ge=0)
    last_request_id: int | None = Field(default=None, gt=0)
    created_at: datetime = Field(default_factory=lambda: datetime.now(UTC))
    updated_at: datetime = Field(default_factory=lambda: datetime.now(UTC))


class ContextTurn(ContextModel):
    """MongoDB 中按 requestId 幂等保存的一轮可重建上下文。"""

    request_id: int = Field(gt=0)
    user_id: int = Field(gt=0)
    session_id: int = Field(gt=0)
    user_context: str = Field(min_length=1, max_length=128000)
    assistant_context: str = Field(min_length=1, max_length=128000)
    model_name: str = Field(min_length=1, max_length=200)
    created_at: datetime = Field(default_factory=lambda: datetime.now(UTC))
