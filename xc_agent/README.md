# xc_agent 后端

Java 21、Spring Boot 4.1.1、MyBatis 4.1.0、MySQL、Redis 和 Lombok 项目。当前已实现普通用户认证，以及独立的管理员认证、仪表盘、工单、FAQ、手册和审计接口。

## 已实现接口

统一前缀为 `/api/v1`：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/sms-codes` | 生成验证码，保存 Redis 5 分钟并打印到控制台 |
| POST | `/auth/login` | 账号密码或手机验证码登录 |
| POST | `/auth/refresh` | 使用 HttpOnly Cookie 轮换 Refresh Token |
| POST | `/auth/logout` | 撤销 Refresh Token 并清除 Cookie |
| GET | `/faq/categories` | 优先从 Redis 获取用户可见 FAQ 分类 |
| GET | `/faq` | FAQ 分页搜索，Redis 未命中时查询 MySQL 并回填 |
| GET | `/faq/{faqId}` | FAQ 详情，Redis 未命中时查询已发布数据并回填 |
| POST | `/chat/stream` | 创建聊天请求，经 Java 转发 Python Agent SSE |
| GET | `/chat/requests/{requestId}` | 查询聊天请求最终状态和回答 |
| POST | `/chat/requests/{requestId}/cancel` | 取消 Java 与 Python Agent 中的运行 |
| PUT | `/messages/{messageId}/feedback` | 设置或清除助手消息赞踩 |
| POST | `/admin/auth/login` | 管理员账号密码登录 |
| POST | `/admin/auth/refresh` | 轮换管理员 Refresh Token |
| POST | `/admin/auth/logout` | 注销管理员会话 |
| GET | `/admin/dashboard/overview` | 管理仪表盘 |
| GET | `/admin/admins` | 管理员只读列表，仅 ADMIN |
| GET/POST | `/admin/tickets/**` | 工单查询、分配、回复和流转 |
| GET/POST/PATCH | `/admin/faqs/**`、`/admin/manuals/**` | 内容与文件管理 |
| GET | `/admin/audits` | 操作审计，仅 ADMIN |
| GET | `/internal/rag/v1/manuals` | Agent 读取已发布维修手册清单，使用内部 Token |

Access JWT 有效期默认 2 小时。后续 `/api/v1/**` Controller 方法默认需要 `Authorization: Bearer <token>`，认证接口除外。

## 默认开发配置

`src/main/resources/application.yml` 已提供以下本机练习默认值：

| 配置 | 默认值 | 环境变量 |
|---|---|---|
| MySQL URL | `jdbc:mysql://127.0.0.1:3306/xinchuang_customer_service...` | `DB_URL` |
| MySQL 用户名 | `root` | `DB_USERNAME` |
| MySQL 密码 | `123456` | `DB_PASSWORD` |
| Redis 地址 | `192.168.100.128:6379` | `REDIS_HOST`、`REDIS_PORT` |
| Redis 密码 | `123456` | `REDIS_PASSWORD` |
| Redis database | `3` | `REDIS_DATABASE` |
| Access Token | `2h` | `ACCESS_TOKEN_TTL` |
| Refresh Token | `30d` | `REFRESH_TOKEN_TTL` |
| 验证码 | `5m` | `SMS_CODE_TTL` |
| JWT 密钥 | 本地开发密钥 | `JWT_SECRET` |
| 管理端 Refresh Cookie | `XC_ADMIN_REFRESH_TOKEN` | `ADMIN_REFRESH_COOKIE_NAME` |
| 登录失败锁定 | 5 次 / 15 分钟 | `ADMIN_MAX_LOGIN_ATTEMPTS`、`ADMIN_LOGIN_LOCK_DURATION` |
| 手册目录 | `./data/manuals` | `MANUAL_STORAGE_DIRECTORY` |
| 手册大小上限 | `20971520` 字节 | `MANUAL_MAX_FILE_SIZE` |
| FAQ 缓存 TTL | `10m` | `FAQ_CACHE_TTL` |
| FAQ 缓存前缀 | `xc:faq:v1:` | `FAQ_CACHE_KEY_PREFIX` |
| Python Agent 地址 | `http://127.0.0.1:8100/internal/ai/v1` | `AGENT_BASE_URL` |
| Python Agent Token | 空，本地不鉴权 | `AGENT_INTERNAL_TOKEN` |
| Agent 请求超时 | `130s` | `AGENT_REQUEST_TIMEOUT` |
| 浏览器 SSE 超时 | `135s` | `CHAT_STREAM_TIMEOUT` |
| Agent 模型路由 | `default` | `AGENT_MODEL_ROUTE` |
| Agent 知识库 ID | `default` | `AGENT_KNOWLEDGE_BASE_IDS`，多个值用逗号分隔 |
| 最大输出 Token | `1024` | `AGENT_MAX_OUTPUT_TOKENS` |

### Chat 三端调用链

浏览器只访问 Java 的 `/api/v1/**`，不要把 Python Agent 端口暴露给前端。Java 会先在
MySQL 中创建会话、请求和两条消息，再调用 Python 的
`/internal/ai/v1/chat/stream`，并将中间 SSE 事件转换成浏览器协议。只有最终数据库事务
提交成功后，Java 才会向浏览器发送 `done`；失败和取消也会先更新数据库终态。

本地联调时先启动 `agent_service` 的 `uv run agent-service`，再启动本 Java 工程。若在
Python 中启用 `AGENT_INTERNAL_AUTH_ENABLED=true`，两个进程必须配置相同的
`AGENT_INTERNAL_TOKEN`。

默认 JWT 密钥只适合本机练习。部署到其他环境时必须使用不少于 32 字节的随机 `JWT_SECRET`，并把 `AUTH_COOKIE_SECURE` 设置为 `true`。

### FAQ Cache-Aside

用户端 FAQ 分类、分页和 `detail:{faqId}` 详情先读取 Redis，未命中时查询 MySQL 并回填，只返回启用分类下已发布的 FAQ。Redis 暂时不可用时自动回退 MySQL。管理端创建、编辑、发布或归档 FAQ，以及新增、修改、排序或删除 FAQ 分类时，会在数据库事务提交成功后清除分类、列表和详情缓存。

分类使用固定 Key，分页与搜索条件使用 SHA-256 查询 Key；所有已写入的 FAQ Key 同时登记在 `xc:faq:v1:keys` 集合中，便于管理端精确清理而不执行 Redis `KEYS` 全库扫描。

该缓存只使用本服务的 `spring.data.redis` / `REDIS_*` 连接。Python 的
`AGENT_REDIS_URL` 是完全独立的 LangGraph 上下文存储，FAQ 和 RAG 都不会复用它。

### 维修手册 RAG 清单

管理端是维修手册的唯一上传入口，支持 PDF、DOCX、TXT、MD，不再接受旧版 DOC。
`GET /internal/rag/v1/manuals` 只返回已发布、分类启用且未删除的受支持手册；Python Agent
以清单中的 `sha256`、版本号和资源版本维护本地 Chroma，不在 Java 与 Chroma 间双写。

部署时应将 Java 的 `MANUAL_STORAGE_DIRECTORY` 和 Python 的
`AGENT_MANUAL_STORAGE_DIRECTORY` 配置到同一物理目录/共享卷，并在两个进程中设置相同的
`AGENT_INTERNAL_TOKEN`。生产环境建议使用绝对路径；清单只传安全 `objectKey`，不传文件
系统绝对路径。

### 请求与数据库日志

后端默认记录所有 `/api/**` 请求的方法、路径、HTTP 状态、耗时和 Request ID；2xx/3xx 使用 INFO，4xx 使用 WARN，5xx 使用 ERROR。MyBatis 同时记录 Mapper Statement、SQL 类型、耗时和查询/影响行数。为避免泄露账号、密码、Token、手机号或内容正文，日志不会打印请求体、SQL 参数或完整数据库异常消息。

## 首次运行

### 1. 初始化 MySQL

按顺序执行：

```powershell
cmd /c "mysql -h 127.0.0.1 -P 3306 -u root -p123456 < ..\database\mysql\00_create_database.sql"
cmd /c "mysql -h 127.0.0.1 -P 3306 -u root -p123456 xinchuang_customer_service < ..\database\mysql\01_schema.sql"
cmd /c "mysql -h 127.0.0.1 -P 3306 -u root -p123456 xinchuang_customer_service < ..\database\mysql\02_dev_seed.sql"
```

普通用户种子账号为 `13800138000`，密码为 `123456`。管理端提供 `admin`、`admin02`、`support01`、`support02` 等测试账号，密码均为 `123456`；详细状态见 `admin-frontend/README.md`。首次成功登录后，后端会把 `{noop}` 密码自动升级为 BCrypt。

### 2. 检查 Redis

在能够访问 Redis 的机器上执行：

```powershell
redis-cli -h 192.168.100.128 -p 6379 -a 123456 -n 3 PING
```

应返回 `PONG`。如果 Windows 无 `redis-cli`，可在 Redis 虚拟机内执行相同命令。

### 3. 构建和启动

```powershell
cd C:\work_learn\XinChuang_pc\xc_agent
.\mvnw.cmd clean test
.\mvnw.cmd -DskipTests package
.\mvnw.cmd spring-boot:run
```

也可使用本机 Maven：

```powershell
mvn clean test
mvn -DskipTests package
mvn spring-boot:run
```

自动化测试会替换 Mapper 和 Redis 客户端，不连接真实 MySQL/Redis。

## 手工验收

保持 Spring Boot 控制台可见，然后在另一个 PowerShell 窗口执行。

### 手机验证码登录

```powershell
$baseUrl = "http://127.0.0.1:8080/api/v1"
$phone = "13900001234"

Invoke-WebRequest -Method Post `
  -Uri "$baseUrl/auth/sms-codes" `
  -ContentType "application/json" `
  -Body (@{ phone = $phone } | ConvertTo-Json)
```

控制台会出现类似日志：

```text
本地登录验证码 phone=139****1234 code=012345 expiresIn=300s
```

把实际验证码填入下面命令：

```powershell
$loginBody = @{
  mode = "sms"
  phone = $phone
  code = "控制台中的6位验证码"
  rememberDevice = $true
} | ConvertTo-Json

$login = Invoke-RestMethod -Method Post `
  -Uri "$baseUrl/auth/login" `
  -ContentType "application/json" `
  -Body $loginBody `
  -SessionVariable authSession

$accessToken = $login.data.accessToken
$login.data
```

该手机号不存在时会自动插入 `customer_user`。验证码成功使用后会从 Redis 删除。

### 账号密码登录

```powershell
$passwordBody = @{
  mode = "password"
  account = "13800138000"
  password = "123456"
  rememberDevice = $true
} | ConvertTo-Json

$login = Invoke-RestMethod -Method Post `
  -Uri "$baseUrl/auth/login" `
  -ContentType "application/json" `
  -Body $passwordBody `
  -SessionVariable authSession
```

### 刷新和退出

```powershell
$refreshed = Invoke-RestMethod -Method Post `
  -Uri "$baseUrl/auth/refresh" `
  -WebSession $authSession

Invoke-WebRequest -Method Post `
  -Uri "$baseUrl/auth/logout" `
  -WebSession $authSession
```

刷新成功后旧 Refresh Token 会在数据库中标记撤销并关联新 token 摘要；退出后再次刷新应返回 401。

### 数据检查

```sql
USE xinchuang_customer_service;

SELECT id, public_id, account, phone, status, last_login_at, password_hash
FROM customer_user
ORDER BY id DESC;

SELECT id, token_hash, token_family_id, subject_id, expires_at, revoked_at, replaced_by_token_hash
FROM refresh_token
ORDER BY id DESC;

SELECT subject_ref, login_mode, result, reason_code, ip_address, request_id, created_at
FROM login_audit
ORDER BY id DESC;
```

数据库不会保存 Refresh Token 明文、Access JWT、密码或验证码明文。验证码只在开发控制台打印并存入 Redis database 3；`auth_sms_code` 表为后续扩展保留。
