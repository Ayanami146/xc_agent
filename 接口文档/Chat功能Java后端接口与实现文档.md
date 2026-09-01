# Chat 功能 Java 后端接口与实现文档

> 项目：信创电脑客服系统（`xc_agent`）  
> 基础包：`com.xc.agent`  
> 技术栈：Java 21、Spring Boot 4.1.1、MyBatis、MySQL、Redis、JWT  
> 文档范围：用户端 Chat 功能的 Java 后端接口、类职责、数据库操作、SSE 协议及实施顺序  
> 本文档只描述如何实现，不包含本次代码修改。

## 1. 建设目标

当前用户端聊天页面会持续转圈，主要原因是 Chat 后端尚未形成完整闭环：会话、消息、请求状态、流式响应和终止事件均需要由 Java 后端实现。

第一阶段建议先实现“FAQ 直接回答”版本：

1. 用户提交问题。
2. Java 后端创建会话、聊天请求和消息记录。
3. Java 后端优先从 Redis 查询 FAQ，未命中时查询 MySQL，并将结果写入 Redis。
4. Java 后端通过 SSE 逐段返回答案。
5. 最终发送 `done` 或 `error` 事件，前端据此停止转圈。

第一阶段不依赖 Python、向量数据库或大模型；待基础 Chat 链路稳定后，再接入内部 AI 服务。

## 2. 当前项目状态

项目中已存在 Chat 相关 DTO、VO、PO、Mapper 接口和 Mapper XML 的基础文件，但 Chat Controller、Service 以及多数 Mapper SQL 尚未实现。

已具备的公共能力：

- 用户登录、JWT 校验和 `AuthContext`。
- MySQL、Redis 基础配置。
- FAQ 管理及用户端 FAQ 查询。
- FAQ 缓存清理机制。
- 统一响应、统一异常、请求 ID 和请求/数据库日志。

本次需要补齐的核心链路：

```text
浏览器
  → ChatController
  → ChatService
  → ChatRequestInitializer（短事务初始化）
  → ChatGenerationService（异步生成）
  → FaqAnswerService（Redis → MySQL）
  → ChatStreamService（发送 SSE）
  → MyBatis Mapper（保存最终状态）
```

## 3. 建议新增或完善的 Java 文件

```text
com.xc.agent
├─ controller
│  └─ ChatController.java
├─ service
│  ├─ ChatService.java
│  └─ chat
│     ├─ ChatServiceImpl.java
│     ├─ ChatRequestInitializer.java
│     ├─ ChatGenerationService.java
│     ├─ ChatStreamService.java
│     ├─ ChatCancellationService.java
│     └─ FaqAnswerService.java
├─ config
│  └─ ChatAsyncConfig.java
├─ mapper
│  ├─ ChatSessionMapper.java
│  ├─ ChatRequestMapper.java
│  ├─ ChatMessageMapper.java
│  ├─ MessageCitationMapper.java
│  ├─ MessageFeedbackMapper.java
│  └─ ApiIdempotencyRecordMapper.java
└─ model
   ├─ dto/chat
   └─ vo/chat
```

职责说明：

| 类 | 主要职责 |
|---|---|
| `ChatController` | 接收 HTTP 请求、读取当前用户、调用 Service、创建 SSE 连接 |
| `ChatService` | 定义 Chat 业务边界，不包含具体 SQL |
| `ChatServiceImpl` | 会话、历史消息、反馈、请求查询等业务编排 |
| `ChatRequestInitializer` | 在短事务中创建会话、请求、用户消息和助手占位消息 |
| `ChatGenerationService` | 异步执行 FAQ/AI 回答，更新数据库最终状态 |
| `ChatStreamService` | 维护 SSE 连接、事件序号并发送标准事件 |
| `ChatCancellationService` | 设置和检查取消标记 |
| `FaqAnswerService` | Redis 优先、MySQL 兜底查询 FAQ 答案 |
| `ChatAsyncConfig` | 配置 Chat 专用线程池，避免占用 Web 请求线程 |

## 4. 用户端 HTTP 接口

所有接口统一前缀为 `/api/v1`。除登录接口外，以下接口都必须携带：

```http
Authorization: Bearer <accessToken>
```

### 4.1 会话列表

```http
GET /api/v1/sessions?page=1&pageSize=20&keyword=打印机
```

Controller 方法建议：

```java
ApiResponse<PageVO<ChatSessionVO>> listSessions(SessionQueryDTO query)
```

处理规则：

- 从 `AuthContext` 取得当前用户 ID，禁止使用前端传入的用户 ID。
- 只查询当前用户且未删除的会话。
- `keyword` 可匹配会话标题或预览内容。
- 按 `updated_at DESC` 排序。

### 4.2 修改会话标题

```http
PATCH /api/v1/sessions/{sessionId}
Content-Type: application/json

{
  "title": "打印机故障处理"
}
```

Controller 方法建议：

```java
ApiResponse<ChatSessionVO> renameSession(Long sessionId, SessionRenameDTO dto)
```

处理规则：

- 标题必填，最长 30 个字符。
- `sessionId` 对应 `chat_session.id` 自增主键，更新条件必须同时包含会话 ID 和当前用户 ID。
- 无权访问与会话不存在统一返回 404，避免泄露他人资源。

### 4.3 删除会话

```http
DELETE /api/v1/sessions/{sessionId}
```

Controller 方法建议：

```java
ResponseEntity<Void> deleteSession(Long sessionId)
```

处理规则：

- 执行软删除，不物理删除聊天记录。
- 只能删除当前用户自己的会话。
- 成功返回 `204 No Content`。

### 4.4 查询历史消息

```http
GET /api/v1/sessions/{sessionId}/messages?page=1&pageSize=50
```

Controller 方法建议：

```java
ApiResponse<PageVO<ChatMessageVO>> listMessages(Long sessionId, MessageQueryDTO query)
```

处理规则：

- 先校验会话归属。
- 按消息创建时间正序返回，确保对话展示顺序正确。
- 助手消息需要一并返回引用和用户反馈。
- 只返回能够展示的消息状态；失败消息可返回错误状态，但不得返回内部异常堆栈。

### 4.5 发起流式聊天

```http
POST /api/v1/chat/stream
Content-Type: application/json
Accept: text/event-stream

{
  "sessionId": null,
  "message": "打印机无法打印怎么办？"
}
```

Controller 方法建议：

```java
SseEmitter stream(ChatStreamDTO dto)
```

处理规则：

- `sessionId` 可为空；为空时创建新会话。
- `message` 必填，最长 8000 字符。
- HTTP 响应类型必须为 `text/event-stream;charset=UTF-8`。
- Controller 立即返回 `SseEmitter`，回答生成放到独立线程池执行。
- 流必须以 `done` 或 `error` 结束，否则前端会一直转圈。

### 4.6 查询聊天请求结果

```http
GET /api/v1/chat/requests/{requestId}
```

Controller 方法建议：

```java
ApiResponse<ChatRequestResultVO> getRequestResult(Long requestId)
```

用途：

- SSE 意外断开后，前端可查询请求最终状态。
- 页面刷新后恢复未完成或已完成的回答。
- 只允许查询当前用户自己的请求。

### 4.7 取消聊天请求

```http
POST /api/v1/chat/requests/{requestId}/cancel
```

Controller 方法建议：

```java
ResponseEntity<Void> cancelRequest(Long requestId)
```

处理规则：

- 校验请求归属。
- 已结束请求应按幂等方式返回成功，或返回明确的状态冲突。
- 对运行中的请求写入 Redis 取消标记。
- 生成线程检测到取消标记后停止输出，更新请求及消息状态。

### 4.8 消息反馈

```http
PUT /api/v1/messages/{messageId}/feedback
Content-Type: application/json

{
  "feedback": "HELPFUL"
}
```

Controller 方法建议：

```java
ApiResponse<ChatMessageVO> saveFeedback(Long messageId, MessageFeedbackDTO dto)
```

处理规则：

- 仅允许对当前用户会话中的助手消息反馈。
- 建议支持 `HELPFUL`、`UNHELPFUL`；若 DTO 允许空值，可将空值定义为取消反馈。
- 同一用户对同一消息只能保留一条反馈，重复提交执行更新。

## 5. ChatService 接口定义

建议接口只表达业务含义，不暴露 Mapper 或数据库结构：

```java
public interface ChatService {

    PageVO<ChatSessionVO> listSessions(SessionQueryDTO query);

    ChatSessionVO renameSession(Long sessionId, SessionRenameDTO dto);

    void deleteSession(Long sessionId);

    PageVO<ChatMessageVO> listMessages(Long sessionId, MessageQueryDTO query);

    SseEmitter startStream(Long userId, ChatStreamDTO dto);

    ChatRequestResultVO getRequestResult(Long userId, Long requestId);

    void cancelRequest(Long userId, Long requestId);

    ChatMessageVO saveFeedback(Long userId, Long messageId,
                               MessageFeedbackDTO dto);
}
```

建议拆分的内部接口：

```java
public interface FaqAnswerService {
    FaqAnswerResult findAnswer(String question);
}

public interface ChatCancellationService {
    void requestCancel(Long requestId);
    boolean isCancellationRequested(Long requestId);
    void clear(Long requestId);
}

public interface ChatStreamService {
    void sendMeta(ChatExecutionContext context);
    void sendStatus(ChatExecutionContext context, String stage);
    void sendDelta(ChatExecutionContext context, String text);
    void sendCitation(ChatExecutionContext context, CitationVO citation);
    void sendDone(ChatExecutionContext context, ChatRequestResultVO result);
    void sendError(ChatExecutionContext context, String code, String message);
}
```

## 6. Mapper 接口与 SQL 职责

Mapper 方法统一使用数据库自增主键；涉及用户资源时仍必须包含用户归属条件。

### 6.1 ChatSessionMapper

| 方法 | 作用 |
|---|---|
| `selectPageByUserId` | 分页查询当前用户会话 |
| `countByUserId` | 统计当前用户会话数量 |
| `selectByIdAndUserId` | 按会话主键和用户 ID 查询会话 |
| `insert` | 创建新会话 |
| `updateTitle` | 修改标题，同时校验用户归属 |
| `updatePreviewAndTime` | 回答完成后更新预览和更新时间 |
| `softDelete` | 软删除当前用户会话 |

### 6.2 ChatRequestMapper

| 方法 | 作用 |
|---|---|
| `selectByIdAndUserId` | 按请求主键和用户 ID 查询聊天请求 |
| `insert` | 写入 `ACCEPTED` 请求 |
| `updateRunning` | 更新为 `RUNNING` |
| `updateSucceeded` | 保存成功状态、结束时间和结果关联 |
| `updateFailed` | 保存失败码、失败说明和结束时间 |
| `updateCancelled` | 保存取消状态 |
| `updateInterrupted` | 保存流中断状态 |

### 6.3 ChatMessageMapper

| 方法 | 作用 |
|---|---|
| `selectPageBySessionId` | 分页查询会话消息 |
| `countBySessionId` | 统计会话消息数 |
| `selectByIdAndUserId` | 按消息主键查询属于当前用户的消息 |
| `insert` | 插入用户消息或助手占位消息 |
| `updateCompleted` | 保存完整回答并更新为 `COMPLETED` |
| `updateFailed` | 更新为 `FAILED` |
| `updateInterrupted` | 更新为 `INTERRUPTED` |

### 6.4 MessageCitationMapper

| 方法 | 作用 |
|---|---|
| `insertBatch` | 批量写入回答引用 |
| `selectByMessageId` | 查询单条消息引用 |
| `selectByMessageIds` | 批量查询消息引用，避免 N+1 查询 |

### 6.5 MessageFeedbackMapper

| 方法 | 作用 |
|---|---|
| `selectByMessageIdAndUserId` | 查询现有反馈 |
| `insert` | 首次添加反馈 |
| `update` | 修改反馈 |
| `delete` | 取消反馈 |

### 6.6 ApiIdempotencyRecordMapper

当前主键简化版本的 Chat 模块不使用该 Mapper。若项目中保留了通用幂等记录表，它仅作为其他业务的预留能力，不参与聊天请求处理。

## 7. 数据库写入流程

### 7.1 请求初始化事务

一次流式聊天开始时，在一个短事务中执行：

1. `sessionId` 为空则创建 `chat_session`；不为空则按主键校验会话归属。
2. 创建 `chat_request`，由 MySQL 生成自增请求 ID，状态为 `ACCEPTED`。
3. 创建用户消息，状态为 `COMPLETED`。
4. 创建助手占位消息，状态为 `STREAMING`。
5. 提交事务。

提交后再启动异步生成任务。不能在整个 SSE 生命周期中持有数据库事务，否则会长时间占用连接并增加锁冲突风险。

### 7.2 生成成功事务

回答完成后，在另一个短事务中执行：

1. 将助手完整回答写入 `chat_message`，状态改为 `COMPLETED`。
2. 写入 `message_citation`。
3. 将 `chat_request` 更新为 `SUCCEEDED`。
4. 更新 `chat_session.preview` 和 `updated_at`。
5. 提交事务。
6. 发送 SSE `done` 事件并关闭连接。

### 7.3 失败或取消事务

- 业务失败：请求更新为 `FAILED`，助手消息更新为 `FAILED`。
- 用户取消：请求更新为 `CANCELLED`，助手消息更新为 `INTERRUPTED`。
- 网络中断：可继续后台生成并允许前端稍后查询结果；若选择停止，则更新为 `INTERRUPTED`。第一阶段建议继续生成，避免短暂断网导致结果丢失。

## 8. 状态机

聊天请求状态：

```text
ACCEPTED → RUNNING → SUCCEEDED
                   → FAILED
                   → CANCELLED
                   → INTERRUPTED
```

聊天消息状态：

```text
用户消息：COMPLETED

助手消息：STREAMING → COMPLETED
                    → FAILED
                    → INTERRUPTED
```

状态更新 SQL 应附带当前状态条件，例如只允许把 `RUNNING` 更新为 `SUCCEEDED`，避免取消和成功同时覆盖。

## 9. SSE 事件协议

### 9.1 统一事件结构

每个事件都使用以下外层结构：

```json
{
  "event": "delta",
  "requestId": 21,
  "sequence": 3,
  "occurredAt": "2026-08-26T10:30:00Z",
  "payload": {}
}
```

要求：

- 同一请求的 `sequence` 从 1 开始严格递增。
- `requestId` 在整个流中保持一致。
- `occurredAt` 使用 ISO-8601 时间格式。
- 最后一个业务事件必须是 `done` 或 `error`。

### 9.2 事件类型

| 事件 | 发送时机 | payload 主要字段 |
|---|---|---|
| `meta` | 初始化完成 | `sessionId`、`requestId`、`assistantMessageId` |
| `status` | 阶段变化 | `stage`、`message` |
| `delta` | 返回答案片段 | `content` |
| `citation` | 返回一条引用 | `title`、`snippet`、`page`、`sourceLocator` |
| `usage` | 有模型用量时 | token 等统计；FAQ 阶段可不发送 |
| `heartbeat` | 长时间无内容时 | 可带当前时间，用于保持连接 |
| `done` | 成功结束 | 完整结果、消息 ID、引用等 |
| `error` | 失败结束 | `code`、对用户安全的 `message` |

建议成功顺序：

```text
meta → status(RETRIEVING) → status(GENERATING)
     → delta... → citation... → done
```

建议失败顺序：

```text
meta → status(...) → error
```

注意：只调用 `SseEmitter.complete()` 而不发送 `done/error`，前端可能无法判断业务是否结束，仍会显示加载状态。

## 10. FAQ 直接回答实现

### 10.1 查询顺序

```text
用户问题
  → 标准化文本
  → Redis 查询答案缓存
     ├─ 命中：直接返回
     └─ 未命中：查询 MySQL 中已发布 FAQ
                  ├─ 查到：写入 Redis，再返回
                  └─ 未查到：返回统一兜底回答
```

### 10.2 Redis Key 建议

```text
xc:faq:v1:answer:{questionHash}
```

- `questionHash`：标准化问题文本的 SHA-256。
- Value：FAQ ID、标题、完整答案、来源、更新时间的 JSON。
- TTL：建议 10 分钟，与现有 FAQ 缓存策略保持一致。
- 管理端新增、修改、发布、归档或删除 FAQ 后，清理 `xc:faq:v1:*` 下相关缓存。

### 10.3 MySQL 查询范围

只允许查询：

- 状态为已发布的 FAQ。
- 未删除的数据。
- 启用分类下的数据。

匹配顺序建议：

1. 问题完全匹配。
2. 标题完全匹配。
3. 关键词匹配。
4. 简单模糊匹配。
5. 无结果时返回兜底说明，例如建议用户换一种描述或提交工单。

第一阶段的目的不是实现复杂语义搜索，而是先打通完整聊天流程。

## 11. 异步线程池

建议建立 Chat 专用线程池：

```yaml
app:
  chat:
    async:
      core-pool-size: 4
      max-pool-size: 8
      queue-capacity: 100
      thread-name-prefix: chat-
    stream-timeout: 120s
    heartbeat-interval: 15s
```

关键要求：

- 不使用默认公共线程池。
- 队列满时返回 `CHAT_CAPACITY_EXCEEDED`，不要无限堆积。
- 异步线程无法自动继承 ThreadLocal 中的 `AuthContext`，应在提交任务前把必要的用户 ID、请求 ID放入不可变的 `ChatExecutionContext`。
- 异步任务结束后清理 Redis 取消标记和内存中的 SSE 连接。

## 12. 取消机制

Redis Key：

```text
chat:cancel:{requestId}
```

建议行为：

- 取消接口写入值 `1`，TTL 可设为 5 分钟。
- 生成服务在每次输出 `delta` 前检查一次。
- 检测到取消后停止生成，更新数据库状态，发送终止事件。
- 任务结束后删除取消 Key。

第一阶段单机练习也可以使用 `ConcurrentHashMap`，但使用 Redis 更方便以后扩展多实例，并与当前项目环境保持一致。

## 13. ID 与重复提交处理

Chat 会话、请求和消息一律使用 MySQL `BIGINT AUTO_INCREMENT` 主键。当前练习版本不再引入字符串资源 ID，也不使用 `clientMessageId`。如需防止按钮重复提交，可由前端在请求期间禁用发送按钮；以后确有需求时再使用通用 `Idempotency-Key` 机制。

## 14. 权限与安全要求

- 所有用户资源必须按当前 JWT 用户隔离。
- 不接受前端提交的 `userId` 作为权限依据。
- 会话、请求、消息的查询和更新 SQL 均需带用户归属条件。
- 404 同时用于“资源不存在”和“资源不属于当前用户”，避免资源枚举。
- SSE 只发送对用户安全的错误信息；完整异常写入后端日志。
- 日志不得输出 JWT、Cookie、用户完整问题正文或完整 AI 回答。
- Chat 请求主键和系统请求追踪 ID 应写入日志 MDC，便于排查一次聊天链路。

## 15. 错误码建议

| HTTP 状态 | 错误码 | 场景 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | 参数格式、长度或枚举非法 |
| 401 | `AUTH_UNAUTHORIZED` | JWT 缺失、过期或无效 |
| 404 | `RESOURCE_NOT_FOUND` | 会话、请求或消息不存在/无权访问 |
| 409 | `CHAT_REQUEST_STATE_CONFLICT` | 当前状态不允许继续操作 |
| 429 | `CHAT_CAPACITY_EXCEEDED` | Chat 线程池或队列已满 |
| 503 | `FAQ_SERVICE_UNAVAILABLE` | Redis/MySQL 均不可用且无法回答 |
| 503 | `MODEL_UNAVAILABLE` | 后续 AI 服务不可用 |
| 500 | `INTERNAL_ERROR` | 未识别的服务端错误 |

SSE 已建立连接后发生错误，HTTP 状态通常无法再修改，应发送 `error` 事件，并把相同错误码放入 payload。

## 16. Controller 实现注意事项

`POST /chat/stream` 的典型处理骨架：

```java
@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@Valid @RequestBody ChatStreamDTO dto) {
    AuthPrincipal principal = AuthContext.required();
    return chatService.startStream(
            principal.userId(),
            dto
    );
}
```

Controller 只负责：

- 接收和校验参数。
- 读取认证用户。
- 调用 Service。
- 返回标准响应或 `SseEmitter`。

不要在 Controller 中直接写 SQL、访问 Redis、拼接 FAQ 答案或启动裸线程。

## 17. 第二阶段：接入内部 AI 服务

基础 Chat 链路稳定后，将 `FaqAnswerService` 替换或扩展为 AI Connector。Java 对外接口不需要变化。

Java 调用 Python 的内部接口建议：

```http
POST /internal/ai/v1/chat/stream
POST /internal/ai/v1/chat/requests/{requestId}/cancel
```

Java 发送内容包括：

- `requestId`
- 当前问题
- 最近若干条历史消息
- FAQ/手册检索上下文
- 模型路由参数
- 超时和输出长度限制

内部接口需要：

- HMAC 签名。
- 时间戳与 nonce 防重放。
- 连接超时、读取超时和总超时。
- 熔断、有限重试和明确错误映射。
- Java 仍然是数据库状态、用户权限、SSE 对外协议的最终负责人。

## 18. 推荐实施顺序

### 第一步：完成持久层

1. 补齐 6 个 Chat Mapper 接口方法。
2. 补齐对应 Mapper XML SQL。
3. 编写 Mapper XML 加载测试。
4. 手工确认现有表字段、枚举值和逻辑删除字段一致。

### 第二步：完成普通查询功能

1. 会话列表。
2. 修改会话标题。
3. 删除会话。
4. 历史消息。
5. 消息反馈。

完成后先用 Postman 或浏览器验证，不必等待流式聊天完成。

### 第三步：完成请求初始化

1. 新建或校验当前用户的会话。
2. 创建请求和两条消息，全部使用数据库返回的自增主键。
3. 验证事务回滚。

### 第四步：完成 FAQ 回答服务

1. 定义 `FaqAnswerResult`。
2. 实现 Redis 命中。
3. 实现 MySQL 回源。
4. 实现回填缓存。
5. 确认管理端修改 FAQ 后能够清缓存。

### 第五步：完成 SSE

1. 配置专用线程池。
2. 创建 `SseEmitter`。
3. 严格发送 `meta/status/delta/citation/done`。
4. 实现异常时 `error`。
5. 实现超时、断开和资源清理。

### 第六步：完成恢复和取消

1. 请求结果查询。
2. Redis 取消标记。
3. 取消后的状态更新。
4. SSE 断开后结果恢复。

### 第七步：再接入 AI

不要在基础会话、消息和 SSE 尚未稳定时直接接入大模型，否则问题会同时散落在前端、Java、Redis、MySQL 和 AI 服务中，难以定位。

## 19. 自动化测试清单

- 新会话发送消息时创建会话、请求和两条消息。
- 已有会话发送消息时校验用户归属。
- 不同用户无法读取、修改或删除对方会话。
- FAQ Redis 命中时不访问 MySQL。
- FAQ Redis 未命中时查询 MySQL，并正确回填缓存。
- 管理端修改 FAQ 后旧答案缓存失效。
- SSE `sequence` 严格递增。
- 成功流最后一个事件为 `done`。
- 失败流最后一个事件为 `error`。
- 取消后停止发送 `delta`，数据库状态正确。
- SSE 连接断开不会造成数据库长事务或连接泄漏。
- 反馈新增、修改、取消均符合唯一性要求。
- Mapper XML 能被 MyBatis 正常加载。

## 20. 用户手工验收流程

1. 启动 MySQL，确认 `xinchuang_customer_service` 中已有 Chat 和 FAQ 相关表。
2. 启动 Redis，确认使用 database 3。
3. 在管理端创建并发布至少两条 FAQ。
4. 启动后端，确认启动日志中 Mapper XML 无解析错误。
5. 使用手机号登录用户端，取得有效 Access JWT。
6. 进入聊天页，发送一条与 FAQ 标题完全一致的问题。
7. 浏览器开发者工具中确认出现 `POST /api/v1/chat/stream`。
8. 查看响应事件，确认至少包含 `meta`、`status`、`delta`、`done`。
9. 确认前端在 `done` 后停止转圈并显示完整回答。
10. 检查数据库中的会话、请求、用户消息、助手消息和引用记录。
11. 再次发送相同问题，检查 Redis 中 FAQ 答案缓存并确认命中日志。
12. 在管理端修改该 FAQ，再次提问，确认返回新答案而不是旧缓存。
13. 刷新页面，确认历史会话和消息能够恢复。
14. 测试重命名、删除会话和消息反馈。
15. 发送问题后立即取消，确认前端停止输出且数据库状态为取消/中断。

## 21. 完成标准

满足以下条件即可认为第一阶段 Chat 功能完成：

- 用户端不再使用预设聊天数据。
- 页面发送问题时确实请求 Java 后端。
- 首次消息能自动创建会话。
- FAQ 查询遵循 Redis 优先、MySQL 兜底。
- 回答通过 SSE 渐进显示。
- 每个流都有 `done` 或 `error` 终止事件，页面不会无限转圈。
- 刷新页面后能从数据库恢复会话和消息。
- 取消、反馈、重命名和删除功能可用。
- 所有数据均按登录用户隔离。
- 后端日志能够通过请求 ID、聊天请求 ID 定位问题，但不泄露敏感信息。
