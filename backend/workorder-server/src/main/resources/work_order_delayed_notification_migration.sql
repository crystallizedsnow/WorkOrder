USE wos;

-- 延期工单飞书提醒后端迁移。本脚本可以重复执行。
-- 固定 ID 必须与 application.yaml 的 workorder.auth.system-sender-id 一致。
SET @workorder_system_sender_id = 900000000000000001;
SET @workorder_system_staff_number = 'SYSTEM_WORKORDER_DELAYED_NOTICE';

-- 兼容尚未执行认证迁移的数据库。
SET @auth_version_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'staff'
      AND COLUMN_NAME = 'auth_version'
);
SET @add_auth_version_sql = IF(
    @auth_version_exists = 0,
    'ALTER TABLE staff ADD COLUMN auth_version INT NOT NULL DEFAULT 0 COMMENT ''认证版本''',
    'SELECT 1'
);
PREPARE add_auth_version_stmt FROM @add_auth_version_sql;
EXECUTE add_auth_version_stmt;
DEALLOCATE PREPARE add_auth_version_stmt;

-- 创建专用系统发送人。status=2（停职），无法通过登录和令牌校验。
-- 使用 company 第一条记录满足 staff.company_code 的非空约束。
INSERT INTO staff (
    id, staff_number, password, name, company_code, company,
    department_code, department, position, status,
    manager_number, manager_name, phone, email, role, auth_version,
    create_time, update_time
)
SELECT
    @workorder_system_sender_id,
    @workorder_system_staff_number,
    '!SYSTEM_ACCOUNT_NO_INTERACTIVE_LOGIN_9f3c7a2e!',
    '工单系统',
    c.code,
    c.name,
    NULL,
    NULL,
    '延期通知系统账号',
    2,
    NULL,
    NULL,
    NULL,
    NULL,
    'user',
    0,
    NOW(),
    NOW()
FROM company c
WHERE NOT EXISTS (
    SELECT 1 FROM staff s
    WHERE s.id = @workorder_system_sender_id
       OR s.staff_number = @workorder_system_staff_number
)
ORDER BY c.id
LIMIT 1;

-- 重复执行时，确保账号不会被误启用或赋予管理员角色。
UPDATE staff
SET name = '工单系统',
    position = '延期通知系统账号',
    status = 2,
    role = 'user',
    auth_version = auth_version + 1,
    phone = NULL,
    email = NULL,
    update_time = NOW()
WHERE id = @workorder_system_sender_id
  AND staff_number = @workorder_system_staff_number;

CREATE TABLE IF NOT EXISTS notification_delivery (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    work_order_id BIGINT NOT NULL,
    work_order_code VARCHAR(64) NOT NULL,
    receiver_id BIGINT NOT NULL,
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME NULL,
    sending_started_at DATETIME NULL,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at DATETIME NULL,
    UNIQUE KEY uk_notification_delivery (event_type, work_order_id, receiver_id, channel),
    KEY idx_notification_retry (channel, status, next_retry_time),
    KEY idx_notification_event (event_id),
    CONSTRAINT fk_notification_order FOREIGN KEY (work_order_id) REFERENCES work_order(id),
    CONSTRAINT fk_notification_receiver FOREIGN KEY (receiver_id) REFERENCES staff(id)
);

SET @sending_started_at_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notification_delivery'
      AND COLUMN_NAME = 'sending_started_at'
);
SET @add_sending_started_at_sql = IF(
    @sending_started_at_exists = 0,
    'ALTER TABLE notification_delivery ADD COLUMN sending_started_at DATETIME NULL AFTER next_retry_time',
    'SELECT 1'
);
PREPARE add_sending_started_at_stmt FROM @add_sending_started_at_sql;
EXECUTE add_sending_started_at_stmt;
DEALLOCATE PREPARE add_sending_started_at_stmt;

SET @overdue_index_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'work_order'
      AND INDEX_NAME = 'idx_work_order_overdue'
);
SET @add_overdue_index_sql = IF(
    @overdue_index_exists = 0,
    'ALTER TABLE work_order ADD INDEX idx_work_order_overdue (status, deadline_time, id)',
    'SELECT 1'
);
PREPARE add_overdue_index_stmt FROM @add_overdue_index_sql;
EXECUTE add_overdue_index_stmt;
DEALLOCATE PREPARE add_overdue_index_stmt;

-- 验收：第一条查询应返回且仅返回一行，status 必须为 2。
SELECT id, staff_number, name, status, role, company_code, company
FROM staff
WHERE id = @workorder_system_sender_id
  AND staff_number = @workorder_system_staff_number;

SHOW CREATE TABLE notification_delivery;
