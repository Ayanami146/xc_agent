-- ============================================================
-- Chat 模块主键简化迁移（仅执行一次）
-- 目标：Chat 对外接口和表关联统一使用 BIGINT 自增主键。
-- 执行前请先备份数据库；本脚本会删除旧的字符串公开 ID 和幂等字段。
-- ============================================================

USE xinchuang_customer_service;

ALTER TABLE chat_session
  DROP INDEX uk_chat_session_public_id,
  DROP COLUMN public_id;

ALTER TABLE chat_request
  DROP INDEX uk_chat_request_request_id,
  DROP INDEX uk_chat_request_user_idem,
  DROP INDEX uk_chat_request_user_client_msg,
  DROP COLUMN request_id,
  DROP COLUMN client_message_id,
  DROP COLUMN idempotency_key;

ALTER TABLE chat_message
  DROP INDEX uk_chat_message_public_id,
  DROP COLUMN public_id;

ALTER TABLE message_citation
  DROP COLUMN document_version_id,
  ADD COLUMN source_id BIGINT UNSIGNED NULL
    COMMENT '引用来源的内部主键，暂无来源时允许为空'
    AFTER ordinal_no;

-- 检查迁移结果：Chat 资源应只剩 BIGINT 主键/外键。
SHOW COLUMNS FROM chat_session;
SHOW COLUMNS FROM chat_request;
SHOW COLUMNS FROM chat_message;
SHOW COLUMNS FROM message_citation;
