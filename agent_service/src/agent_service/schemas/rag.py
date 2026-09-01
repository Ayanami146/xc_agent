"""Java 已发布手册清单和 Agent 检索结果结构。"""

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class RagManual(BaseModel):
    """Java 内部清单返回的一条已发布维修手册。"""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")

    source_id: int = Field(gt=0)
    document_id: str = Field(min_length=1)
    title: str = Field(min_length=1)
    summary: str | None = None
    object_key: str = Field(pattern=r"^[a-f0-9]{32}\.(pdf|docx|txt|md)$")
    file_name: str = Field(min_length=1)
    content_type: str = Field(min_length=1)
    sha256: str = Field(pattern=r"^[a-f0-9]{64}$")
    version_no: int = Field(ge=1)
    resource_version: int = Field(ge=0)

    @property
    def fingerprint(self) -> str:
        """返回用于判断 Chroma 切片是否过期的稳定指纹。"""

        return f"{self.sha256}:{self.resource_version}"


class RetrievedManualChunk(BaseModel):
    """传给 LangGraph 的一条手册检索结果。"""

    source_id: int
    document_id: str
    title: str
    content: str
    source_locator: str
    page: int | None = None
    score: float
