# 信创智能客服 Python Agent Service

该目录是与 `xc_agent/` Java 工程平级的独立 Python 模块。它使用 `uv` 管理 Python
版本、虚拟环境和依赖，不参与 Java 的 Maven/Gradle 构建。

当前版本已经包含：

- FastAPI 内部服务与健康检查；
- LangGraph 的 `input_guard → route_query → DIRECT/RAG → generate` 条件工作流；
- RAG 分支查询重写、阿里云 `text-embedding-v3` 稠密向量、Chroma 增量索引和手册引用；
- LangChain 模型适配层，支持离线 Mock 和 OpenAI 兼容模型；
- 后端活动角色文件，修改后在下一次聊天请求自动生效；
- `meta/status/delta/done/error` SSE 事件；
- Redis LangGraph checkpoint、同会话分布式锁和重复运行保护；
- MongoDBStore 永久轮次归档与 Redis 热状态丢失后的冷恢复；
- 与现有 Java `AiChatStreamDTO` 对齐的请求结构；
- 基础单元测试和 Ruff 代码检查。

## 1. 初始化环境

```powershell
cd C:\work_learn\XinChuang_pc\agent_service
uv sync --dev
Copy-Item .env.example .env
```

`uv sync` 会根据 `pyproject.toml` 和 `uv.lock` 创建项目专用 `.venv`。不要手工向全局
Python 安装依赖。项目通过 `.python-version` 固定使用 uv 管理的 CPython 3.12.14；
Windows 下不要改用 Anaconda Python 创建该虚拟环境，否则 Chroma 的本地二进制扩展
可能在写入向量时发生原生访问冲突，进程会直接退出而无法被 Python 异常处理捕获。

复制后必须修改 `.env` 中的 Redis、MongoDB 和真实模型占位符。Redis URL 最后的数据库
编号必须是 `/0`；LangGraph Redis Saver 使用 Redis Search，而 Redis Search 不能在非 0
逻辑数据库上创建索引。

同时把 Java 的 `MANUAL_STORAGE_DIRECTORY` 与 Agent 的
`AGENT_MANUAL_STORAGE_DIRECTORY` 配置为指向同一物理目录的绝对路径。可以是单机目录，
也可以是两个进程都能访问的共享卷；两边路径字符串不要求相同，但最终必须指向同一批文件。

## 2. 启动服务

```powershell
uv run agent-service
```

默认监听 `http://127.0.0.1:8100`，接口文档位于 `/docs`。

当前归档通道仍是 Redis Stream。需要永久归档时另开一个终端启动消费者：

```powershell
uv run agent-context-consumer
```

本轮只保证现有通道可运行，尚未将其替换为 RabbitMQ。

## 3. 修改当前角色

角色完全由后端文件 [`config/active_role.json`](config/active_role.json) 控制，聊天请求不需要
传 `roleId` 或其他角色参数。可直接修改：

```json
{
  "name": "技术支持专家",
  "description": "偏向故障诊断和操作步骤。",
  "systemPrompt": "你是一名技术支持专家。回答时先判断问题原因，再给出分步骤排查方案。"
}
```

Agent 会在每次新运行开始前重新读取该文件，因此保存后下一次请求立即使用新角色，无需重启
服务。已经开始的流式回答继续使用启动时取得的角色快照，不会在回答中途发生角色变化。

如需把角色文件放到其他位置，可配置 `AGENT_ROLE_CONFIG_PATH`。

## 4. 运行检查

```powershell
uv run ruff check .
uv run pytest
```

## 5. 最小接口

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/internal/ai/v1/health` | 健康检查 |
| POST | `/internal/ai/v1/chat/stream` | 执行 LangGraph 并返回 SSE |
| POST | `/internal/ai/v1/chat/requests/{requestId}/cancel` | 请求取消运行 |

`chat/stream` 的请求字段保持 camelCase，以便 Java record 直接序列化。当前的 `history`
字段只为兼容已有 DTO 而接收，智能体不会使用它恢复模型上下文。正式上下文由 Redis
checkpoint 快速读取；checkpoint 不存在时，从 MongoDB 永久轮次恢复。禁止从 MySQL
`chat_message` 构造模型历史。

Python 内部 SSE 中的 `meta` 和 `done` 不包含 MySQL 消息主键。Java 网关收到流后负责：

1. 生成面向浏览器的 `meta`；
2. 转发 `status/delta/citation/usage`；
3. 将最终答案和引用写入 MySQL；
4. 获得最终消息 ID 后再生成浏览器端 `done`。

RAG 命中时，Agent 会在首个 `delta` 前发送 `citation`。其中 `sourceId` 是手册内部主键，
`sourceLocator` 为 `manual:{publicId}`，并按文件格式提供页码和相关度分数；Java 继续负责
引用落库和向浏览器转发。

## 6. 切换真实模型

在 `.env` 中设置：

```dotenv
AGENT_MODEL_PROVIDER=openai
AGENT_MODEL_NAME=your-model-name
AGENT_OPENAI_API_KEY=your-api-key
```

如果使用兼容 OpenAI API 的私有模型网关，可额外设置 `AGENT_OPENAI_BASE_URL`。密钥只能
来自环境变量或密钥管理系统，禁止写入代码、日志、Trace 和版本库。

## 7. 启用维修手册 RAG

Java 管理端仍是唯一上传入口。手册发布后，Agent 在下一次 RAG 请求中读取
`GET /internal/rag/v1/manuals` 清单，按 `sha256 + resourceVersion` 增量更新本地 Chroma：

- 新手册或新版本会重新解析、切分并调用 `text-embedding-v3`；
- 未变化手册不会重复向量化；
- 归档、分类禁用或不再发布的手册会删除全部旧切片；
- 清单不可用时，本轮不会查询可能过期的 Chroma，而是明确降级为通用回答。

在 `.env` 中至少设置：

```dotenv
AGENT_RAG_ENABLED=true
AGENT_EMBEDDING_API_KEY=your-dashscope-api-key
AGENT_JAVA_RAG_MANIFEST_URL=http://127.0.0.1:8080/internal/rag/v1/manuals
AGENT_MANUAL_STORAGE_DIRECTORY=C:/absolute/shared/manuals
AGENT_CHROMA_DIRECTORY=C:/absolute/agent-data/chroma
```

默认使用 `text-embedding-v3`、1024 维、800 字符切片、120 字符重叠、Top 5 和 0.35
相关度阈值。当前支持 PDF、DOCX、TXT、MD，不支持旧版二进制 DOC。

`AGENT_REDIS_URL` 仅保存 LangGraph 上下文、锁和归档 Stream。FAQ 的缓存仍由 Java
`spring.data.redis`/`REDIS_*` 独立管理，RAG 索引也不会写入任何 Redis。

## 8. 当前边界

- 取消令牌仍在进程内，多实例部署前需要改为共享状态；
- 归档消息通道当前仍为 Redis Stream，RabbitMQ 迁移不在本轮修改范围；
- 模型输入裁剪直接使用 LangChain `trim_messages`，没有自研摘要或压缩框架；
- 当前 RAG 是确定性的 LangGraph 节点，不需要工具调用，`toolsEnabled=false` 保持不变；
- 生产环境应将内部 Token 升级为 TLS + HMAC + Redis nonce 防重放。
