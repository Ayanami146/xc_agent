"""应用配置定义。

所有配置均通过 ``AGENT_`` 前缀的环境变量注入，避免在代码中保存模型密钥或环境差异。
``pydantic-settings`` 会在服务启动时完成类型转换和必要的配置校验。
"""

from functools import lru_cache
from pathlib import Path
from typing import Literal
from urllib.parse import urlsplit

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


def _default_role_config_path() -> Path:
    """返回随项目分发的默认活动角色文件绝对路径。"""

    # config.py 位于 <project>/src/agent_service 下，parents[2] 即 agent_service 项目根目录。
    return Path(__file__).resolve().parents[2] / "config" / "active_role.json"


def _default_manual_storage_path() -> Path:
    """返回与平级 Java 工程共享的默认维修手册目录。"""

    return Path(__file__).resolve().parents[3] / "xc_agent" / "data" / "manuals"


def _default_chroma_path() -> Path:
    """返回 Agent 自己管理的本地 Chroma 持久化目录。"""

    return Path(__file__).resolve().parents[2] / "data" / "chroma"


class Settings(BaseSettings):
    """智能体服务运行配置。

    所有环境变量均使用 ``AGENT_`` 前缀。

    默认使用 Mock 模型，方便开发和测试。切换到 OpenAI 兼容模型时，
    必须提供 API Key 和真实模型名称。

    上下文持久化启用后：
    - Redis 负责 LangGraph 热上下文和归档消息队列；
    - MongoDB 负责永久保存已经完成的对话轮次。
    """

    model_config = SettingsConfigDict(
        env_prefix="AGENT_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # -------------------------------------------------------------------------
    # 应用基础配置
    # -------------------------------------------------------------------------

    app_name: str = "xinchuang-agent-service"

    environment: Literal[
        "development",
        "test",
        "production",
    ] = "development"

    host: str = "127.0.0.1"

    port: int = Field(
        default=8100,
        ge=1,
        le=65535,
    )

    log_level: str = "INFO"

    # -------------------------------------------------------------------------
    # 模型配置
    # -------------------------------------------------------------------------

    # mock：
    #   使用项目内置的测试模型，不需要 API Key。
    #
    # openai：
    #   使用 OpenAI API 或兼容 OpenAI 协议的模型服务。
    model_provider: Literal["mock", "openai"] = "mock"

    model_name: str = "mock-model"

    # SecretStr 防止密钥通过 repr、日志等方式直接输出。
    openai_api_key: SecretStr | None = None

    # 使用官方 OpenAI API 时可以不配置。
    # 使用兼容 OpenAI 协议的模型服务时填写其接口地址。
    openai_base_url: str | None = None

    model_timeout_seconds: float = Field(
        default=120.0,
        gt=0,
    )

    # 模型允许接收的最大输入 Token 数量。
    #
    # 工作流中的 trim_messages() 使用 LangChain 提供的
    # approximate token counter，根据该配置裁剪模型输入。
    #
    # 该字段不是 Redis 上下文的存储上限，也不是自定义摘要阈值。
    model_max_input_tokens: int = Field(
        default=8000,
        ge=1024,
    )

    # -------------------------------------------------------------------------
    # 维修手册 RAG
    # -------------------------------------------------------------------------

    # 测试和纯 Mock 开发默认关闭 RAG，部署时在 .env 中显式开启。FAQ 缓存不使用这些
    # 配置，它始终属于 Java 业务服务自己的 Redis。
    rag_enabled: bool = False

    java_rag_manifest_url: str = "http://127.0.0.1:8080/internal/rag/v1/manuals"
    manual_storage_directory: Path = Field(default_factory=_default_manual_storage_path)
    chroma_directory: Path = Field(default_factory=_default_chroma_path)
    chroma_collection: str = "manuals_text_embedding_v3_1024_v1"

    embedding_api_key: SecretStr | None = None
    embedding_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    embedding_model: str = "text-embedding-v3"
    embedding_dimensions: int = Field(default=1024, ge=64, le=2048)

    rag_chunk_size: int = Field(default=800, ge=200, le=4000)
    rag_chunk_overlap: int = Field(default=120, ge=0, le=1000)
    rag_top_k: int = Field(default=5, ge=1, le=20)
    rag_score_threshold: float = Field(default=0.35, ge=0.0, le=1.0)

    # -------------------------------------------------------------------------
    # 角色配置
    # -------------------------------------------------------------------------

    # 活动角色由后端配置文件统一控制，不由前端请求传入。
    #
    # 默认路径由 _default_role_config_path() 生成，也可以通过
    # AGENT_ROLE_CONFIG_PATH 环境变量覆盖。
    role_config_path: Path = Field(
        default_factory=_default_role_config_path,
    )

    # -------------------------------------------------------------------------
    # 内部接口鉴权
    # -------------------------------------------------------------------------

    internal_auth_enabled: bool = False

    # 正式环境应当通过 AGENT_INTERNAL_TOKEN 提供随机长令牌，
    # 不应继续使用这里的默认值。
    internal_token: SecretStr = SecretStr("replace-with-a-long-random-token")

    # -------------------------------------------------------------------------
    # 上下文持久化总开关
    # -------------------------------------------------------------------------

    # True：
    #   启用 Redis 热上下文、Redis Streams 和 MongoDB 永久归档。
    #
    # False：
    #   不创建 Redis/MongoDB 上下文组件，主要供单元测试使用。
    context_storage_enabled: bool = True

    # -------------------------------------------------------------------------
    # Redis 热上下文配置
    # -------------------------------------------------------------------------

    # Redis URL 可以同时包含：
    # - Redis 地址；
    # - 端口；
    # - 密码；
    # - 数据库编号。
    #
    # 示例：
    # redis://:password@192.168.100.128:6379/0
    #
    # 因为 URL 可能包含密码，所以必须使用 SecretStr。
    redis_url: SecretStr | None = None

    # AsyncShallowRedisSaver 使用分钟作为 TTL 单位。
    #
    # 10080 分钟 = 7 天。
    #
    # TTL 只控制 Redis 热上下文的保留时间，不影响 MongoDB 中
    # 永久保存的对话轮次。
    context_redis_ttl_minutes: int = Field(
        default=10080,
        ge=10,
    )

    # -------------------------------------------------------------------------
    # Redis Streams 异步归档配置
    # -------------------------------------------------------------------------

    # API 完成一轮对话后，把归档事件写入该 Stream。
    context_archive_stream: str = "xc:agent:context:archive:v1"

    # MongoDB 归档消费者组名称。
    context_archive_group: str = "mongo-archive-v1"

    # 当前归档进程的消费者名称。
    #
    # 如果部署多个消费者进程，每个实例必须配置不同名称，
    # 例如 mongo-archive-1、mongo-archive-2。
    context_archive_consumer: str = "mongo-archive-1"

    # -------------------------------------------------------------------------
    # 上下文恢复配置
    # -------------------------------------------------------------------------

    # Redis checkpoint 过期或不存在时，从 MongoDB 恢复的最大对话轮数。
    #
    # 一轮包含一条 HumanMessage 和一条 AIMessage。
    context_rehydrate_turn_limit: int = Field(
        default=200,
        ge=1,
        le=2000,
    )

    # -------------------------------------------------------------------------
    # 会话并发控制
    # -------------------------------------------------------------------------

    # 同一个 userId + sessionId 的 Redis 分布式锁超时时间。
    #
    # 该锁用于防止同一会话的两个请求同时更新同一个 LangGraph thread。
    session_lock_timeout_seconds: int = Field(
        default=180,
        ge=30,
    )

    # -------------------------------------------------------------------------
    # MongoDB 永久归档配置
    # -------------------------------------------------------------------------

    # MongoDB URI 可能包含用户名和密码，因此使用 SecretStr。
    #
    # 设置为可选类型，是为了允许单元测试通过
    # context_storage_enabled=False 关闭持久化。
    mongodb_uri: SecretStr | None = None

    mongodb_database: str = "xinchuang_agent_context"

    # MongoDBStore 用于保存已完成对话轮次的集合名称。
    #
    # 该集合不设置 TTL，因此保存的是永久轮次。
    mongodb_context_collection: str = "conversation_turns"

    # -------------------------------------------------------------------------
    # 统一配置校验
    # -------------------------------------------------------------------------

    @model_validator(mode="after")
    def validate_runtime_settings(self) -> "Settings":
        """在服务启动阶段校验当前运行模式需要的配置。

        只检查当前模式真正需要的字段：

        - Mock 模型不需要 OpenAI 密钥；
        - OpenAI 模型必须提供密钥和真实模型名称；
        - 关闭上下文持久化时不要求 Redis、MongoDB；
        - 启用上下文持久化时必须提供 Redis、MongoDB URI。
        """

        # 使用 OpenAI 或兼容 OpenAI 协议的模型服务时，
        # 必须提供 API Key。
        if self.model_provider == "openai":
            if self.openai_api_key is None or not self.openai_api_key.get_secret_value():
                raise ValueError("AGENT_MODEL_PROVIDER=openai 时必须配置 AGENT_OPENAI_API_KEY")

            # mock-model 只是项目内置测试模型的名称，
            # 真实模型模式不能继续使用该名称。
            if self.model_name == "mock-model":
                raise ValueError("真实模型模式必须配置实际的 AGENT_MODEL_NAME")

        if self.rag_enabled:
            if self.embedding_api_key is None or not self.embedding_api_key.get_secret_value():
                raise ValueError("启用 RAG 时必须配置 AGENT_EMBEDDING_API_KEY")
            if self.rag_chunk_overlap >= self.rag_chunk_size:
                raise ValueError("AGENT_RAG_CHUNK_OVERLAP 必须小于 AGENT_RAG_CHUNK_SIZE")

        # 上下文存储关闭时，允许不配置 Redis 和 MongoDB。
        # 该模式主要用于单元测试。
        if not self.context_storage_enabled:
            return self

        # 上下文存储启用时，Redis 是在线热上下文和异步队列，
        # 因此 Redis URL 不能为空。
        if self.redis_url is None or not self.redis_url.get_secret_value():
            raise ValueError("启用上下文持久化时必须配置 AGENT_REDIS_URL")

        # LangGraph Redis Saver 会通过 Redis Search 为 checkpoint 创建索引。
        # Redis Search 不支持非 0 逻辑数据库，因此在配置阶段直接拒绝 /1、/4 等地址，
        # 避免等到 FastAPI lifespan 启动时才得到难以理解的 FT.CREATE 错误。
        redis_url = self.redis_url.get_secret_value()
        parsed_redis_url = urlsplit(redis_url)
        if parsed_redis_url.scheme not in {"redis", "rediss"}:
            raise ValueError("AGENT_REDIS_URL 必须使用 redis:// 或 rediss://")

        database_text = parsed_redis_url.path.lstrip("/")
        try:
            redis_database = int(database_text) if database_text else 0
        except ValueError as exc:
            raise ValueError("AGENT_REDIS_URL 中的数据库编号必须是整数") from exc
        if redis_database != 0:
            raise ValueError(
                "LangGraph Redis Saver 使用 Redis Search，AGENT_REDIS_URL 必须选择 database 0"
            )

        # MongoDB 是永久轮次存储，因此 MongoDB URI 不能为空。
        if self.mongodb_uri is None or not self.mongodb_uri.get_secret_value():
            raise ValueError("启用上下文持久化时必须配置 AGENT_MONGODB_URI")

        return self


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """返回进程内共享的只读配置实例。"""

    return Settings()
