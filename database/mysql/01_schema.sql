-- 信创智能客服练习项目：核心表结构
-- 目标版本：MySQL 8.0+
-- 时间统一保存 UTC；后端返回 ISO-8601。

USE xinchuang_customer_service;
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ============================================================
-- 1. 用户、管理员和认证
-- ============================================================

CREATE TABLE IF NOT EXISTS customer_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部主键',
  public_id VARCHAR(40) NOT NULL COMMENT '对外用户ID',
  account VARCHAR(64) NOT NULL COMMENT '登录账号',
  phone VARCHAR(32) NULL COMMENT '练习环境手机号；生产应改为密文',
  phone_hash CHAR(64) NULL COMMENT '手机号SHA-256/带Pepper摘要，用于查询',
  password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt或Argon2哈希',
  nickname VARCHAR(64) NOT NULL,
  avatar_text VARCHAR(4) NOT NULL DEFAULT '用',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  last_login_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_customer_user_public_id (public_id),
  UNIQUE KEY uk_customer_user_account (account),
  UNIQUE KEY uk_customer_user_phone_hash (phone_hash),
  KEY idx_customer_user_status (status, deleted_at),
  CONSTRAINT ck_customer_user_status CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED'))
) ENGINE=InnoDB COMMENT='普通用户';

CREATE TABLE IF NOT EXISTS admin_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(40) NOT NULL,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  role_code VARCHAR(20) NOT NULL DEFAULT 'SUPPORT',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  failed_count INT UNSIGNED NOT NULL DEFAULT 0,
  locked_until DATETIME(3) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_user_public_id (public_id),
  UNIQUE KEY uk_admin_user_username (username),
  KEY idx_admin_user_role_status (role_code, status),
  CONSTRAINT ck_admin_user_role CHECK (role_code IN ('SUPPORT', 'ADMIN')),
  CONSTRAINT ck_admin_user_status CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED'))
) ENGINE=InnoDB COMMENT='客服和管理员';

CREATE TABLE IF NOT EXISTS refresh_token (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  token_hash CHAR(64) NOT NULL COMMENT '只保存refresh token摘要',
  token_family_id VARCHAR(40) NOT NULL COMMENT '轮换链ID',
  subject_type VARCHAR(20) NOT NULL,
  subject_id BIGINT UNSIGNED NOT NULL,
  device_id VARCHAR(100) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  revoked_at DATETIME(3) NULL,
  replaced_by_token_hash CHAR(64) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_token_hash (token_hash),
  KEY idx_refresh_subject_device (subject_type, subject_id, device_id),
  KEY idx_refresh_expires (expires_at),
  CONSTRAINT ck_refresh_subject_type CHECK (subject_type IN ('CUSTOMER', 'ADMIN'))
) ENGINE=InnoDB COMMENT='可轮换刷新令牌';

CREATE TABLE IF NOT EXISTS auth_sms_code (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  phone_hash CHAR(64) NOT NULL,
  code_hash CHAR(64) NOT NULL COMMENT '验证码摘要，不保存明文',
  purpose VARCHAR(20) NOT NULL DEFAULT 'LOGIN',
  expires_at DATETIME(3) NOT NULL,
  used_at DATETIME(3) NULL,
  failed_count INT UNSIGNED NOT NULL DEFAULT 0,
  request_id VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_sms_phone_purpose_created (phone_hash, purpose, created_at),
  KEY idx_sms_expires (expires_at),
  CONSTRAINT ck_sms_purpose CHECK (purpose IN ('LOGIN', 'RESET_PASSWORD'))
) ENGINE=InnoDB COMMENT='短信验证码';

CREATE TABLE IF NOT EXISTS login_audit (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  subject_type VARCHAR(20) NOT NULL,
  subject_ref VARCHAR(100) NOT NULL COMMENT '脱敏账号或不可逆摘要',
  login_mode VARCHAR(20) NOT NULL,
  result VARCHAR(20) NOT NULL,
  reason_code VARCHAR(64) NULL,
  ip_address VARCHAR(64) NULL,
  user_agent VARCHAR(500) NULL,
  request_id VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_login_audit_subject_created (subject_type, subject_ref, created_at),
  KEY idx_login_audit_request (request_id),
  CONSTRAINT ck_login_audit_result CHECK (result IN ('SUCCESS', 'FAILED'))
) ENGINE=InnoDB COMMENT='登录审计';

-- ============================================================
-- 2. 会话、问答请求和消息
-- ============================================================

CREATE TABLE IF NOT EXISTS chat_session (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(100) NOT NULL,
  preview VARCHAR(255) NOT NULL DEFAULT '',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  last_message_at DATETIME(3) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  KEY idx_chat_session_user_last (user_id, deleted_at, last_message_at),
  CONSTRAINT fk_chat_session_user FOREIGN KEY (user_id) REFERENCES customer_user (id),
  CONSTRAINT ck_chat_session_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB COMMENT='用户对话会话';

CREATE TABLE IF NOT EXISTS chat_request (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  session_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  request_hash CHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED',
  started_at DATETIME(3) NULL,
  finished_at DATETIME(3) NULL,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(500) NULL,
  model_route VARCHAR(100) NULL,
  usage_json JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_chat_request_session_created (session_id, created_at),
  KEY idx_chat_request_status_created (status, created_at),
  CONSTRAINT fk_chat_request_session FOREIGN KEY (session_id) REFERENCES chat_session (id),
  CONSTRAINT fk_chat_request_user FOREIGN KEY (user_id) REFERENCES customer_user (id),
  CONSTRAINT ck_chat_request_status CHECK (status IN ('ACCEPTED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED'))
) ENGINE=InnoDB COMMENT='一次流式问答请求';

CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  session_id BIGINT UNSIGNED NOT NULL,
  request_id BIGINT UNSIGNED NULL,
  role VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
  content MEDIUMTEXT NOT NULL,
  stage VARCHAR(20) NULL,
  intent_type VARCHAR(50) NULL,
  model_name VARCHAR(100) NULL,
  token_count INT UNSIGNED NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_chat_message_session_id (session_id, id),
  KEY idx_chat_message_request (request_id),
  CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session (id),
  CONSTRAINT fk_chat_message_request FOREIGN KEY (request_id) REFERENCES chat_request (id),
  CONSTRAINT ck_chat_message_role CHECK (role IN ('user', 'assistant')),
  CONSTRAINT ck_chat_message_status CHECK (status IN ('COMPLETED', 'STREAMING', 'INTERRUPTED', 'FAILED')),
  CONSTRAINT ck_chat_message_stage CHECK (stage IS NULL OR stage IN ('queued', 'safety', 'intent', 'retrieval', 'generation', 'validation'))
) ENGINE=InnoDB COMMENT='聊天消息';

CREATE TABLE IF NOT EXISTS message_citation (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  message_id BIGINT UNSIGNED NOT NULL,
  ordinal_no INT UNSIGNED NOT NULL,
  source_id BIGINT UNSIGNED NULL COMMENT '引用来源的内部主键，暂无来源时允许为空',
  title VARCHAR(255) NOT NULL,
  snippet TEXT NOT NULL,
  source_locator VARCHAR(500) NOT NULL,
  page_no INT UNSIGNED NULL,
  score DECIMAL(8,6) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_message_citation_ordinal (message_id, ordinal_no),
  CONSTRAINT fk_message_citation_message FOREIGN KEY (message_id) REFERENCES chat_message (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='回答引用快照';

CREATE TABLE IF NOT EXISTS message_feedback (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  message_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  feedback VARCHAR(10) NULL COMMENT 'NULL表示清除反馈',
  comment VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_message_feedback_user (message_id, user_id),
  KEY idx_feedback_user_updated (user_id, updated_at),
  CONSTRAINT fk_message_feedback_message FOREIGN KEY (message_id) REFERENCES chat_message (id) ON DELETE CASCADE,
  CONSTRAINT fk_message_feedback_user FOREIGN KEY (user_id) REFERENCES customer_user (id),
  CONSTRAINT ck_message_feedback CHECK (feedback IS NULL OR feedback IN ('up', 'down'))
) ENGINE=InnoDB COMMENT='回答赞踩';

-- ============================================================
-- 3. FAQ 和维修手册
-- ============================================================

CREATE TABLE IF NOT EXISTS faq_category (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(40) NOT NULL,
  name VARCHAR(100) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_faq_category_public_id (public_id),
  UNIQUE KEY uk_faq_category_name (name),
  KEY idx_faq_category_status_sort (status, sort_order),
  CONSTRAINT ck_faq_category_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB COMMENT='FAQ分类';

CREATE TABLE IF NOT EXISTS faq_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(40) NOT NULL,
  category_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(255) NOT NULL,
  question VARCHAR(1000) NOT NULL,
  answer MEDIUMTEXT NOT NULL,
  summary VARCHAR(500) NOT NULL DEFAULT '',
  keywords VARCHAR(500) NOT NULL DEFAULT '',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  is_top TINYINT(1) NOT NULL DEFAULT 0,
  hot_count INT UNSIGNED NOT NULL DEFAULT 0,
  published_at DATETIME(3) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_faq_item_public_id (public_id),
  KEY idx_faq_item_category_status (category_id, status, is_top, updated_at),
  FULLTEXT KEY ft_faq_item_search (title, question, answer, keywords) WITH PARSER ngram,
  CONSTRAINT fk_faq_item_category FOREIGN KEY (category_id) REFERENCES faq_category (id),
  CONSTRAINT ck_faq_item_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
) ENGINE=InnoDB COMMENT='FAQ内容';

CREATE TABLE IF NOT EXISTS manual_category (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(40) NOT NULL,
  name VARCHAR(100) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_manual_category_public_id (public_id),
  UNIQUE KEY uk_manual_category_name (name),
  KEY idx_manual_category_status_sort (status, sort_order),
  CONSTRAINT ck_manual_category_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB COMMENT='手册分类';

CREATE TABLE IF NOT EXISTS manual_doc (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(40) NOT NULL,
  category_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(255) NOT NULL,
  summary VARCHAR(1000) NOT NULL DEFAULT '',
  parsed_text MEDIUMTEXT NULL,
  object_key VARCHAR(500) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  file_size BIGINT UNSIGNED NOT NULL DEFAULT 0,
  sha256 CHAR(64) NULL,
  scan_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  version_no INT UNSIGNED NOT NULL DEFAULT 1,
  published_at DATETIME(3) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_manual_doc_public_id (public_id),
  KEY idx_manual_doc_category_status (category_id, status, updated_at),
  FULLTEXT KEY ft_manual_doc_search (title, summary, parsed_text) WITH PARSER ngram,
  CONSTRAINT fk_manual_doc_category FOREIGN KEY (category_id) REFERENCES manual_category (id),
  CONSTRAINT ck_manual_scan_status CHECK (scan_status IN ('PENDING', 'PASSED', 'REJECTED')),
  CONSTRAINT ck_manual_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
) ENGINE=InnoDB COMMENT='维修手册';

-- ============================================================
-- 4. 留言工单、回复、附件和状态历史
-- ============================================================

CREATE TABLE IF NOT EXISTS ticket (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(40) NOT NULL COMMENT '对外工单编号，例如WO202608250001',
  user_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(100) NOT NULL,
  category VARCHAR(50) NOT NULL,
  device_brand VARCHAR(100) NOT NULL,
  device_model VARCHAR(150) NOT NULL,
  description TEXT NOT NULL,
  contact VARCHAR(100) NOT NULL COMMENT '练习环境明文；生产应加密',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  assignee_id BIGINT UNSIGNED NULL,
  resolution VARCHAR(2000) NULL,
  resolved_at DATETIME(3) NULL,
  closed_at DATETIME(3) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ticket_public_id (public_id),
  KEY idx_ticket_user_status_updated (user_id, status, updated_at),
  KEY idx_ticket_assignee_status (assignee_id, status, updated_at),
  CONSTRAINT fk_ticket_user FOREIGN KEY (user_id) REFERENCES customer_user (id),
  CONSTRAINT fk_ticket_assignee FOREIGN KEY (assignee_id) REFERENCES admin_user (id),
  CONSTRAINT ck_ticket_status CHECK (status IN ('PENDING', 'PROCESSING', 'WAITING_USER', 'RESOLVED', 'CLOSED'))
) ENGINE=InnoDB COMMENT='留言工单';

CREATE TABLE IF NOT EXISTS ticket_reply (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(40) NOT NULL,
  ticket_id BIGINT UNSIGNED NOT NULL,
  sender_type VARCHAR(20) NOT NULL,
  customer_user_id BIGINT UNSIGNED NULL,
  admin_user_id BIGINT UNSIGNED NULL,
  sender_name_snapshot VARCHAR(100) NOT NULL,
  content TEXT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ticket_reply_public_id (public_id),
  KEY idx_ticket_reply_ticket_created (ticket_id, created_at),
  CONSTRAINT fk_ticket_reply_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id) ON DELETE CASCADE,
  CONSTRAINT fk_ticket_reply_customer FOREIGN KEY (customer_user_id) REFERENCES customer_user (id),
  CONSTRAINT fk_ticket_reply_admin FOREIGN KEY (admin_user_id) REFERENCES admin_user (id),
  CONSTRAINT ck_ticket_reply_sender CHECK (
    (sender_type = 'user' AND customer_user_id IS NOT NULL AND admin_user_id IS NULL)
    OR (sender_type = 'admin' AND customer_user_id IS NULL AND admin_user_id IS NOT NULL)
  )
) ENGINE=InnoDB COMMENT='工单沟通记录';

CREATE TABLE IF NOT EXISTS ticket_attachment (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(40) NOT NULL,
  uploader_user_id BIGINT UNSIGNED NOT NULL,
  ticket_id BIGINT UNSIGNED NULL COMMENT '上传后绑定工单；NULL为临时附件',
  reply_id BIGINT UNSIGNED NULL,
  object_key VARCHAR(500) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  file_size BIGINT UNSIGNED NOT NULL,
  sha256 CHAR(64) NOT NULL,
  scan_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  bind_status VARCHAR(20) NOT NULL DEFAULT 'TEMP',
  expires_at DATETIME(3) NULL COMMENT '临时附件过期时间',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ticket_attachment_public_id (public_id),
  KEY idx_attachment_ticket (ticket_id, created_at),
  KEY idx_attachment_temp_cleanup (bind_status, expires_at),
  CONSTRAINT fk_attachment_uploader FOREIGN KEY (uploader_user_id) REFERENCES customer_user (id),
  CONSTRAINT fk_attachment_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id) ON DELETE CASCADE,
  CONSTRAINT fk_attachment_reply FOREIGN KEY (reply_id) REFERENCES ticket_reply (id) ON DELETE SET NULL,
  CONSTRAINT ck_attachment_scan CHECK (scan_status IN ('PENDING', 'PASSED', 'REJECTED')),
  CONSTRAINT ck_attachment_bind CHECK (bind_status IN ('TEMP', 'BOUND', 'REJECTED')),
  CONSTRAINT ck_attachment_bound_ticket CHECK (bind_status <> 'BOUND' OR ticket_id IS NOT NULL)
) ENGINE=InnoDB COMMENT='工单附件';

CREATE TABLE IF NOT EXISTS ticket_status_history (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(40) NOT NULL,
  ticket_id BIGINT UNSIGNED NOT NULL,
  from_status VARCHAR(20) NULL,
  to_status VARCHAR(20) NOT NULL,
  operator_type VARCHAR(20) NOT NULL,
  operator_id BIGINT UNSIGNED NULL,
  title VARCHAR(100) NOT NULL,
  reason VARCHAR(1000) NOT NULL DEFAULT '',
  request_id VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ticket_history_public_id (public_id),
  KEY idx_ticket_history_ticket_created (ticket_id, created_at),
  KEY idx_ticket_history_request (request_id),
  CONSTRAINT fk_ticket_history_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id) ON DELETE CASCADE,
  CONSTRAINT ck_ticket_history_to CHECK (to_status IN ('PENDING', 'PROCESSING', 'WAITING_USER', 'RESOLVED', 'CLOSED')),
  CONSTRAINT ck_ticket_history_operator CHECK (operator_type IN ('SYSTEM', 'CUSTOMER', 'ADMIN'))
) ENGINE=InnoDB COMMENT='工单状态时间线';

-- ============================================================
-- 5. API幂等和操作审计
-- ============================================================

CREATE TABLE IF NOT EXISTS api_idempotency_record (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  scope_name VARCHAR(100) NOT NULL COMMENT '接口作用域',
  caller_id VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  resource_type VARCHAR(50) NULL,
  resource_id VARCHAR(64) NULL,
  response_status SMALLINT UNSIGNED NULL,
  response_body JSON NULL,
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_api_idempotency (scope_name, caller_id, idempotency_key),
  KEY idx_api_idempotency_expires (expires_at)
) ENGINE=InnoDB COMMENT='写接口幂等结果';

CREATE TABLE IF NOT EXISTS operation_audit (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  request_id VARCHAR(64) NULL,
  actor_type VARCHAR(20) NOT NULL,
  actor_id VARCHAR(64) NULL,
  action_name VARCHAR(100) NOT NULL,
  resource_type VARCHAR(50) NOT NULL,
  resource_id VARCHAR(64) NULL,
  result VARCHAR(20) NOT NULL,
  detail_json JSON NULL COMMENT '不得保存密码、token和完整敏感正文',
  ip_address VARCHAR(64) NULL,
  user_agent VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_operation_audit_request (request_id),
  KEY idx_operation_audit_actor_created (actor_type, actor_id, created_at),
  KEY idx_operation_audit_resource (resource_type, resource_id, created_at),
  CONSTRAINT ck_operation_audit_actor CHECK (actor_type IN ('CUSTOMER', 'ADMIN', 'SYSTEM')),
  CONSTRAINT ck_operation_audit_result CHECK (result IN ('SUCCESS', 'FAILED'))
) ENGINE=InnoDB COMMENT='关键操作审计';
