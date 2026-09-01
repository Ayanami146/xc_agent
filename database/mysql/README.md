# MySQL 数据库使用说明

## 1. 文件说明

| 文件 | 用途 | 是否必须 |
|---|---|---|
| `00_create_database.sql` | 创建 `xinchuang_customer_service` 数据库 | 是 |
| `01_schema.sql` | 创建核心表、索引、外键和检查约束 | 是 |
| `02_dev_seed.sql` | 插入本机练习账号和页面演示数据 | 开发环境建议 |
| `03_simplify_chat_primary_ids.sql` | 将旧版现有数据库的 Chat 表迁移为纯自增主键（仅执行一次） | 仅旧库升级需要 |
| `数据库表说明文档.md` | 说明每张表保存的数据、作用、关联关系和对应接口 | 阅读参考 |

目标数据库为 MySQL 8.0+。脚本使用 `utf8mb4`、`CHECK` 约束和 `ngram` 中文全文索引，不建议直接在 MariaDB 上执行。

## 2. Windows 命令行导入

假设 MySQL 安装目录已经加入 PATH：

```powershell
cd C:\work_learn\XinChuang_pc\database\mysql
mysql -u root -p < 00_create_database.sql
mysql -u root -p xinchuang_customer_service < 01_schema.sql
mysql -u root -p xinchuang_customer_service < 02_dev_seed.sql
```

PowerShell 7 对原生程序的 `<` 支持因环境而异。如果重定向不可用，进入 MySQL 客户端后执行：

```sql
SOURCE C:/work_learn/XinChuang_pc/database/mysql/00_create_database.sql;
SOURCE C:/work_learn/XinChuang_pc/database/mysql/01_schema.sql;
SOURCE C:/work_learn/XinChuang_pc/database/mysql/02_dev_seed.sql;
```

SQL 文件路径使用 `/`，避免 Windows 反斜杠被 MySQL 当作转义字符。

如果数据库已经执行过旧版 `01_schema.sql`，请先备份数据库，然后执行一次：

```sql
SOURCE C:/work_learn/XinChuang_pc/database/mysql/03_simplify_chat_primary_ids.sql;
```

全新数据库直接执行新版 `00 → 01 → 02`，不要再执行 `03`。

## 3. MySQL Workbench 导入

1. 连接本机 MySQL。
2. 依次打开三个 SQL 文件。
3. 按 `00 → 01 → 02` 的顺序执行。
4. 刷新 Schemas，确认出现 `xinchuang_customer_service`。
5. 展开 Tables，应看到 20 张核心表。

## 4. 开发账号

导入 `02_dev_seed.sql` 后：

```text
普通用户：13800138000 / 123456
客服账号：admin / 123456
```

种子密码使用：

```text
{noop}123456
```

这只适合 Spring Security `DelegatingPasswordEncoder` 的本机开发。接入 BCrypt 后，应通过后端编码器生成哈希并更新：

```sql
UPDATE customer_user
SET password_hash = '后端生成的BCrypt哈希'
WHERE public_id = 'user_demo';
```

生产环境绝对不能使用 `{noop}` 或种子账号。

## 5. 建议创建单独的应用账号

不要让后端长期使用 MySQL root。以下 SQL 中先替换密码，再由 root 执行：

```sql
CREATE USER IF NOT EXISTS 'xinchuang_app'@'localhost'
IDENTIFIED BY '请替换为本机开发密码';

GRANT SELECT, INSERT, UPDATE, DELETE
ON xinchuang_customer_service.*
TO 'xinchuang_app'@'localhost';

FLUSH PRIVILEGES;
```

如果后端和 MySQL 不在同一台机器，将 `localhost` 改成后端所在内网地址，不要直接使用 `%`。

## 6. Spring Boot 连接示例

`application-dev.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/xinchuang_customer_service?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false
    username: xinchuang_app
    password: 你的本机开发密码
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate.jdbc.time_zone: UTC
```

如果使用 MyBatis，只保留 `spring.datasource` 即可。建表交给 SQL/Flyway，不建议设置 `ddl-auto: update`。

Redis 在虚拟机时只影响后端，例如：

```yaml
spring:
  data:
    redis:
      host: 你的Redis虚拟机IP
      port: 6379
```

Redis 配置与前端、Nginx、MySQL 建表无关。

## 7. 表和前端接口对应关系

| 前端接口 | 主要表 |
|---|---|
| `/auth/login` | `customer_user`、`login_audit`、`refresh_token` |
| `/auth/sms-codes` | Redis database 3；`auth_sms_code` 当前保留不用 |
| `/auth/refresh` | `refresh_token` |
| `/sessions` | `chat_session` |
| `/sessions/{id}/messages` | `chat_message`、`message_citation`、`message_feedback` |
| `/chat/stream` | `chat_session`、`chat_request`、`chat_message`、`message_citation` |
| `/messages/{id}/feedback` | `message_feedback` |
| `/faq/categories` | `faq_category` |
| `/faq` | `faq_item`、`faq_category` |
| `/manuals` | `manual_doc`、`manual_category` |
| `/tickets` | `ticket` |
| `/ticket-attachments` | `ticket_attachment` |
| `/tickets/{id}/replies` | `ticket_reply`、`ticket_status_history` |
| `/tickets/{id}/close`、`reopen` | `ticket`、`ticket_status_history` |
| 所有要求幂等的写接口 | `api_idempotency_record` |
| 登录、关闭、重开等关键操作 | `operation_audit` |

## 8. 主要关联关系

```text
customer_user
  ├─ chat_session
  │    ├─ chat_request
  │    └─ chat_message
  │          ├─ message_citation
  │          └─ message_feedback
  └─ ticket
       ├─ ticket_reply
       ├─ ticket_attachment
       └─ ticket_status_history

faq_category ─ faq_item
manual_category ─ manual_doc
admin_user ─ ticket.assignee_id
```

## 9. 后端实现顺序建议

1. 实现 `customer_user` 查询和 `/auth/login`。
2. 实现 `refresh_token` 的创建、轮换与注销。
3. 实现 `chat_session`、`chat_message` 的分页查询。
4. 实现 FAQ、手册列表，让右侧知识区域先动态显示。
5. 实现工单列表、详情、回复和状态历史。
6. 最后实现 `chat_request` 和 POST SSE 流式问答。

这样每完成一部分，都能立即在当前前端页面看到真实请求和数据库结果。

## 10. 常用检查 SQL

```sql
USE xinchuang_customer_service;

SHOW TABLES;

SELECT public_id, account, nickname, status
FROM customer_user;

SELECT public_id, title, status, updated_at
FROM ticket
ORDER BY updated_at DESC;

SELECT s.id, s.title, COUNT(m.id) AS message_count
FROM chat_session s
LEFT JOIN chat_message m ON m.session_id = s.id
GROUP BY s.id, s.title;
```

## 11. 常见错误

### `Unknown collation: utf8mb4_0900_ai_ci`

数据库版本低于 MySQL 8.0。建议升级 MySQL；仅为临时练习也可以把 `00_create_database.sql` 的排序规则改为 `utf8mb4_unicode_ci`。

### `Function 'ngram' is not defined` 或全文索引创建失败

确认使用官方 MySQL 8.0。如果当前发行版没有 ngram parser，可暂时删除 `01_schema.sql` 中两个 `WITH PARSER ngram`，普通查询和其他表不受影响。

### 登录始终失败

确认后端使用 `DelegatingPasswordEncoder` 识别 `{noop}123456`；如果只使用 `BCryptPasswordEncoder`，必须把种子密码更新为 BCrypt 哈希。

### 时间相差 8 小时

数据库按 UTC 保存，JDBC URL 和 Hibernate 也必须使用 UTC；只有前端展示时转换为 Asia/Shanghai。
