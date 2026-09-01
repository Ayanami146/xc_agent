-- 信创智能客服练习项目：开发演示数据
-- 仅用于本机开发，禁止导入生产环境。
-- 测试账号：13800138000 / 123456
-- 管理端测试账号统一密码：123456
-- password_hash 使用 Spring Security DelegatingPasswordEncoder 的 {noop} 格式。
-- 当你接入 BCrypt 后，请通过后端编码器生成新哈希并更新这两条数据。

USE xinchuang_customer_service;
SET NAMES utf8mb4;
SET time_zone = '+00:00';

INSERT IGNORE INTO customer_user
  (public_id, account, phone, phone_hash, password_hash, nickname, avatar_text, status)
VALUES
  ('user_demo', '13800138000', '13800138000', SHA2('13800138000', 256), '{noop}123456', '练习用户', '练', 'ACTIVE');

INSERT IGNORE INTO admin_user
  (public_id, username, password_hash, display_name, role_code, status)
VALUES
  ('admin_demo', 'admin', '{noop}123456', '王工', 'ADMIN', 'ACTIVE'),
  ('admin_demo_02', 'admin02', '{noop}123456', '李管理员', 'ADMIN', 'ACTIVE'),
  ('support_demo_01', 'support01', '{noop}123456', '张客服', 'SUPPORT', 'ACTIVE'),
  ('support_demo_02', 'support02', '{noop}123456', '赵客服', 'SUPPORT', 'ACTIVE'),
  ('admin_disabled', 'disabled_admin', '{noop}123456', '停用管理员', 'ADMIN', 'DISABLED'),
  ('support_locked', 'locked_support', '{noop}123456', '锁定客服', 'SUPPORT', 'LOCKED');

SET @demo_user_id = (SELECT id FROM customer_user WHERE public_id = 'user_demo');
SET @demo_admin_id = (SELECT id FROM admin_user WHERE public_id = 'admin_demo');

-- FAQ分类和内容
INSERT IGNORE INTO faq_category (public_id, name, sort_order, status) VALUES
  ('faqcat_driver', '驱动问题', 10, 'ENABLED'),
  ('faqcat_network', '网络问题', 20, 'ENABLED'),
  ('faqcat_warranty', '保修咨询', 30, 'ENABLED'),
  ('faqcat_system', '系统故障', 40, 'ENABLED');

SET @faq_driver = (SELECT id FROM faq_category WHERE public_id = 'faqcat_driver');
SET @faq_network = (SELECT id FROM faq_category WHERE public_id = 'faqcat_network');
SET @faq_warranty = (SELECT id FROM faq_category WHERE public_id = 'faqcat_warranty');
SET @faq_system = (SELECT id FROM faq_category WHERE public_id = 'faqcat_system');

INSERT IGNORE INTO faq_item
  (public_id, category_id, title, question, answer, summary, keywords, status, is_top, hot_count, published_at)
VALUES
  ('faq_1', @faq_driver, '驱动安装失败', '统信 UOS 驱动安装失败怎么办？',
   '请先确认系统版本、CPU架构、设备完整型号和驱动包版本。卸载旧驱动并重新连接设备后再安装；仍然失败时请保留安装日志并提交工单。',
   '安装后设备管理器仍有异常标识', '统信 UOS 驱动 打印机 安装失败', 'PUBLISHED', 1, 18, UTC_TIMESTAMP(3)),
  ('faq_2', @faq_network, '网络连接异常', '银河麒麟系统升级后无法连接网络怎么办？',
   '先确认网卡是否被系统识别，再核对IP、DNS和802.1X配置。企业网络环境下请同时确认认证证书和时间是否正确。',
   '系统升级后无法连接企业网络', '银河麒麟 网络 802.1X DNS', 'PUBLISHED', 1, 12, UTC_TIMESTAMP(3)),
  ('faq_3', @faq_warranty, '保修政策咨询', '国产电脑整机保修期限如何计算？',
   '整机、显示器和配件可能采用不同保修期限。请准备设备服务编码，由售后系统按购买时间和产品政策查询。',
   '查询设备保修范围和服务期限', '保修 整机 显示器 服务编码', 'PUBLISHED', 0, 9, UTC_TIMESTAMP(3)),
  ('faq_4', @faq_system, '系统升级卡顿', '系统升级进度长时间不动怎么办？',
   '请先连接电源并等待磁盘活动结束。不要强制断电；超过一小时无变化时记录当前进度和错误信息，再进入恢复环境检查。',
   '升级进度长时间停留在同一页面', '升级 卡顿 恢复环境', 'PUBLISHED', 0, 6, UTC_TIMESTAMP(3));

-- 手册分类和内容。object_key 是练习占位值，可在接入本地文件或 MinIO 后替换。
INSERT IGNORE INTO manual_category (public_id, name, sort_order, status) VALUES
  ('manualcat_driver', '驱动手册', 10, 'ENABLED'),
  ('manualcat_network', '网络手册', 20, 'ENABLED'),
  ('manualcat_service', '服务手册', 30, 'ENABLED');

SET @manual_driver = (SELECT id FROM manual_category WHERE public_id = 'manualcat_driver');
SET @manual_network = (SELECT id FROM manual_category WHERE public_id = 'manualcat_network');
SET @manual_service = (SELECT id FROM manual_category WHERE public_id = 'manualcat_service');

INSERT IGNORE INTO manual_doc
  (public_id, category_id, title, summary, parsed_text, object_key, file_name, content_type, file_size, sha256, scan_status, status, published_at)
VALUES
  ('manual_1', @manual_driver, '统信 UOS 外设驱动安装手册',
   '打印机、扫描仪等外设驱动安装与排障',
   '安装驱动前应确认CPU架构、系统版本和设备硬件ID。安装失败时保留安装日志。',
   'manuals/uos-driver-v1.pdf', '统信UOS外设驱动安装手册.pdf', 'application/pdf', 0, NULL, 'PASSED', 'DRAFT', NULL),
  ('manual_2', @manual_network, '银河麒麟网络配置指南',
   '有线、无线和企业认证网络配置说明',
   '包含IP地址、DNS、无线网络和802.1X企业认证配置。',
   'manuals/kylin-network-v1.pdf', '银河麒麟网络配置指南.pdf', 'application/pdf', 0, NULL, 'PASSED', 'DRAFT', NULL),
  ('manual_3', @manual_service, '国产电脑售后服务手册',
   '保修、送修和配件更换服务规范',
   '介绍服务编码查询、送修流程、数据备份和配件保修政策。',
   'manuals/service-policy-v1.pdf', '国产电脑售后服务手册.pdf', 'application/pdf', 0, NULL, 'PASSED', 'DRAFT', NULL);

-- 一组历史会话和消息
INSERT INTO chat_session (user_id, title, preview, status, last_message_at)
SELECT @demo_user_id, '统信 UOS 打印机驱动', '驱动安装后仍无法识别设备', 'ACTIVE', UTC_TIMESTAMP(3)
WHERE NOT EXISTS (
  SELECT 1 FROM chat_session
  WHERE user_id = @demo_user_id AND title = '统信 UOS 打印机驱动' AND deleted_at IS NULL
);

SET @demo_session_id = (
  SELECT id FROM chat_session
  WHERE user_id = @demo_user_id AND title = '统信 UOS 打印机驱动' AND deleted_at IS NULL
  ORDER BY id LIMIT 1
);

INSERT INTO chat_request
  (session_id, user_id, request_hash, status, started_at, finished_at, model_route)
SELECT @demo_session_id, @demo_user_id,
       SHA2('统信 UOS 打印机驱动安装失败怎么办？', 256),
       'SUCCEEDED', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 'customer-service-default'
WHERE NOT EXISTS (
  SELECT 1 FROM chat_request
  WHERE session_id = @demo_session_id
    AND request_hash = SHA2('统信 UOS 打印机驱动安装失败怎么办？', 256)
);

SET @demo_request_id = (
  SELECT id FROM chat_request
  WHERE session_id = @demo_session_id
    AND request_hash = SHA2('统信 UOS 打印机驱动安装失败怎么办？', 256)
  ORDER BY id LIMIT 1
);

INSERT INTO chat_message
  (session_id, request_id, role, status, content, stage, model_name, token_count, created_at)
SELECT @demo_session_id, @demo_request_id, 'user', 'COMPLETED',
       '统信 UOS 打印机驱动安装失败怎么办？', NULL, NULL, NULL,
       UTC_TIMESTAMP(3) - INTERVAL 1 SECOND
WHERE NOT EXISTS (
  SELECT 1 FROM chat_message
  WHERE request_id = @demo_request_id AND role = 'user'
);

INSERT INTO chat_message
  (session_id, request_id, role, status, content, stage, model_name, token_count, created_at)
SELECT @demo_session_id, @demo_request_id, 'assistant', 'COMPLETED',
       '建议先确认打印机型号、系统架构和当前 UOS 版本，再安装匹配版本的官方驱动。安装前可移除旧驱动；如果仍无法识别，请保留安装日志并提交工单。',
       'validation', 'dev-customer-service', 96, UTC_TIMESTAMP(3)
WHERE NOT EXISTS (
  SELECT 1 FROM chat_message
  WHERE request_id = @demo_request_id AND role = 'assistant'
);

SET @assistant_message_id = (
  SELECT id FROM chat_message
  WHERE request_id = @demo_request_id AND role = 'assistant'
  ORDER BY id LIMIT 1
);

INSERT IGNORE INTO message_citation
  (message_id, ordinal_no, source_id, title, snippet, source_locator, page_no, score)
VALUES
  (@assistant_message_id, 1, NULL, '统信 UOS 外设驱动安装手册',
   '安装驱动前应确认 CPU 架构、系统版本和设备硬件 ID。', '/manuals/manual_1', 12, 0.920000);

-- 工单演示数据
INSERT IGNORE INTO ticket
  (public_id, user_id, title, category, device_brand, device_model, description, contact, status, assignee_id)
VALUES
  ('WO202608240018', @demo_user_id, '统信 UOS 打印机驱动安装失败', '驱动问题', '长城', '世恒 TD120A2',
   '安装官方驱动后打印机仍无法识别，设备管理器显示未知 USB 设备。', '13800138000', 'PROCESSING', @demo_admin_id),
  ('WO202608230009', @demo_user_id, '银河麒麟无法连接企业 Wi-Fi', '网络问题', '浪潮', '英政 CE520F',
   '更新系统后无法通过 802.1X 认证。', '13800138000', 'WAITING_USER', @demo_admin_id),
  ('WO202608210026', @demo_user_id, '整机保修期限确认', '保修咨询', '联想开天', 'M90h G1t',
   '希望确认主机和显示器的保修期限。', '13800138000', 'RESOLVED', @demo_admin_id),
  ('WO202608180013', @demo_user_id, '软件安装提示架构不兼容', '软件兼容', '华为擎云', 'W585x',
   '安装 x86 版本软件时提示不兼容。', '13800138000', 'CLOSED', @demo_admin_id);

SET @ticket_1 = (SELECT id FROM ticket WHERE public_id = 'WO202608240018');
SET @ticket_2 = (SELECT id FROM ticket WHERE public_id = 'WO202608230009');
SET @ticket_3 = (SELECT id FROM ticket WHERE public_id = 'WO202608210026');
SET @ticket_4 = (SELECT id FROM ticket WHERE public_id = 'WO202608180013');

INSERT IGNORE INTO ticket_reply
  (public_id, ticket_id, sender_type, customer_user_id, admin_user_id, sender_name_snapshot, content)
VALUES
  ('reply_demo_1', @ticket_1, 'admin', NULL, @demo_admin_id, '王工', '您好，已收到问题。请确认打印机完整型号，并补充系统版本信息。'),
  ('reply_demo_2', @ticket_2, 'admin', NULL, @demo_admin_id, '王工', '请补充网络认证失败截图和系统版本。'),
  ('reply_demo_3', @ticket_3, 'admin', NULL, @demo_admin_id, '王工', '根据设备服务编码，主机保修至 2028 年 6 月，显示器保修至 2027 年 6 月。');

INSERT IGNORE INTO ticket_status_history
  (public_id, ticket_id, from_status, to_status, operator_type, operator_id, title, reason)
VALUES
  ('tl_demo_1', @ticket_1, NULL, 'PENDING', 'SYSTEM', NULL, '工单已提交', '系统已生成留言工单'),
  ('tl_demo_2', @ticket_1, 'PENDING', 'PROCESSING', 'ADMIN', @demo_admin_id, '客服已受理', '王工正在处理您的问题'),
  ('tl_demo_3', @ticket_2, 'PROCESSING', 'WAITING_USER', 'ADMIN', @demo_admin_id, '等待补充信息', '请上传认证失败截图'),
  ('tl_demo_4', @ticket_3, 'PROCESSING', 'RESOLVED', 'ADMIN', @demo_admin_id, '问题已解决', '已回复保修信息'),
  ('tl_demo_5', @ticket_4, 'RESOLVED', 'CLOSED', 'CUSTOMER', @demo_user_id, '工单已关闭', '已更换为 ARM64 安装包');

-- 验证导入结果
SELECT 'customer_user' AS table_name, COUNT(*) AS row_count FROM customer_user
UNION ALL SELECT 'faq_item', COUNT(*) FROM faq_item
UNION ALL SELECT 'manual_doc', COUNT(*) FROM manual_doc
UNION ALL SELECT 'chat_session', COUNT(*) FROM chat_session
UNION ALL SELECT 'chat_message', COUNT(*) FROM chat_message
UNION ALL SELECT 'ticket', COUNT(*) FROM ticket;
