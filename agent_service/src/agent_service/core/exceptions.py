"""智能体服务内部异常。

领域异常与 FastAPI 解耦，由 API 层统一转换为 SSE ``error`` 事件或 HTTP 响应。
"""


class AgentServiceError(Exception):
    """所有可预期智能体业务异常的基类。"""


class RunAlreadyActiveError(AgentServiceError):
    """同一 requestId 已经存在正在执行的运行。"""


class AgentCancelledError(AgentServiceError):
    """调用方已经请求取消当前智能体运行。"""


class EmptyModelOutputError(AgentServiceError):
    """模型调用正常结束，但没有产生任何可展示文本。"""


class RoleConfigurationError(AgentServiceError):
    """后端活动角色文件不存在、JSON 无效或缺少必要字段。"""


class ContextStoreUnavailableError(AgentServiceError):
    """MongoDB 无法完成必要读取或写入，本轮不能继续。"""


class ContextArchiveUnavailableError(AgentServiceError):
    """完成轮次无法投递到异步归档通道，本轮不能报告成功。"""


class ContextRevisionConflictError(AgentServiceError):
    """同一会话被并发更新，当前快照 revision 已经过期。"""


class ContextTooLargeError(AgentServiceError):
    """压缩和裁剪后仍然超过模型输入硬预算。"""


class RagUnavailableError(AgentServiceError):
    """手册清单、向量化或 Chroma 暂时不可用，本轮应降级为无检索回答。"""
