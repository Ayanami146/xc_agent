"""后端活动角色配置。"""

from pydantic import Field, field_validator

from agent_service.schemas.chat import CamelModel


class RoleProfile(CamelModel):
    """一次 Agent 运行使用的角色快照。

    角色只决定模型的身份、表达方式和行为约束，不承载用户上下文、密钥或业务数据。
    """

    name: str = Field(min_length=1, max_length=100)
    description: str = Field(default="", max_length=500)
    system_prompt: str = Field(min_length=1, max_length=10000)

    @field_validator("name", "system_prompt")
    @classmethod
    def validate_required_text_not_blank(cls, value: str) -> str:
        """防止合法 JSON 中的空白角色名或提示词绕过 min_length。"""

        if not value.strip():
            raise ValueError("角色名称和 systemPrompt 不能为空白字符串")
        return value.strip()
