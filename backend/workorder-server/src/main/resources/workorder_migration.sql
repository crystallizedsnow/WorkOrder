USE wos;

-- ========================================
-- work_order 表迁移
-- ========================================
ALTER TABLE work_order ADD COLUMN create_time_new datetime;
ALTER TABLE work_order ADD COLUMN update_time_new datetime;
ALTER TABLE work_order ADD COLUMN cancel_time_new datetime;
ALTER TABLE work_order ADD COLUMN delete_time_new datetime;
ALTER TABLE work_order ADD COLUMN deadline_time_new datetime;

UPDATE work_order SET 
    create_time_new = FROM_UNIXTIME(create_time),
    update_time_new = FROM_UNIXTIME(update_time),
    cancel_time_new = FROM_UNIXTIME(cancel_time),
    delete_time_new = FROM_UNIXTIME(delete_time),
    deadline_time_new = FROM_UNIXTIME(deadline_time);

ALTER TABLE work_order 
    DROP COLUMN create_time,
    DROP COLUMN update_time,
    DROP COLUMN cancel_time,
    DROP COLUMN delete_time,
    DROP COLUMN deadline_time;

ALTER TABLE work_order 
    CHANGE COLUMN create_time_new create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHANGE COLUMN update_time_new update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHANGE COLUMN cancel_time_new cancel_time datetime,
    CHANGE COLUMN delete_time_new delete_time datetime,
    CHANGE COLUMN deadline_time_new deadline_time datetime;

-- ========================================
-- handle_user_info 表迁移
-- ========================================
ALTER TABLE handle_user_info ADD COLUMN handle_time_new datetime;
ALTER TABLE handle_user_info ADD COLUMN create_time_new datetime;
ALTER TABLE handle_user_info ADD COLUMN update_time_new datetime;

UPDATE handle_user_info SET 
    handle_time_new = FROM_UNIXTIME(handle_time),
    create_time_new = FROM_UNIXTIME(create_time),
    update_time_new = FROM_UNIXTIME(update_time);

ALTER TABLE handle_user_info 
    DROP COLUMN handle_time,
    DROP COLUMN create_time,
    DROP COLUMN update_time;

ALTER TABLE handle_user_info 
    CHANGE COLUMN handle_time_new handle_time datetime,
    CHANGE COLUMN create_time_new create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHANGE COLUMN update_time_new update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- ========================================
-- message 表迁移
-- ========================================
ALTER TABLE message ADD COLUMN send_time_new datetime;
UPDATE message SET send_time_new = FROM_UNIXTIME(send_time);
ALTER TABLE message DROP COLUMN send_time;
ALTER TABLE message CHANGE COLUMN send_time_new send_time datetime;

-- ========================================
-- company 表迁移
-- ========================================
ALTER TABLE company ADD COLUMN create_time_new datetime;
ALTER TABLE company ADD COLUMN update_time_new datetime;
UPDATE company SET 
    create_time_new = FROM_UNIXTIME(create_time),
    update_time_new = FROM_UNIXTIME(update_time);
ALTER TABLE company 
    DROP COLUMN create_time,
    DROP COLUMN update_time;
ALTER TABLE company 
    CHANGE COLUMN create_time_new create_time datetime DEFAULT CURRENT_TIMESTAMP,
    CHANGE COLUMN update_time_new update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- ========================================
-- department 表迁移
-- ========================================
ALTER TABLE department ADD COLUMN create_time_new datetime;
ALTER TABLE department ADD COLUMN update_time_new datetime;
UPDATE department SET 
    create_time_new = FROM_UNIXTIME(create_time),
    update_time_new = FROM_UNIXTIME(update_time);
ALTER TABLE department 
    DROP COLUMN create_time,
    DROP COLUMN update_time;
ALTER TABLE department 
    CHANGE COLUMN create_time_new create_time datetime DEFAULT CURRENT_TIMESTAMP,
    CHANGE COLUMN update_time_new update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- ========================================
-- flow 表迁移
-- ========================================
ALTER TABLE flow ADD COLUMN create_time_new datetime;
ALTER TABLE flow ADD COLUMN update_time_new datetime;
UPDATE flow SET 
    create_time_new = FROM_UNIXTIME(create_time),
    update_time_new = FROM_UNIXTIME(update_time);
ALTER TABLE flow 
    DROP COLUMN create_time,
    DROP COLUMN update_time;
ALTER TABLE flow 
    CHANGE COLUMN create_time_new create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHANGE COLUMN update_time_new update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- ========================================
-- staff 表迁移
-- ========================================
ALTER TABLE staff ADD COLUMN create_time_new datetime;
ALTER TABLE staff ADD COLUMN update_time_new datetime;
UPDATE staff SET 
    create_time_new = FROM_UNIXTIME(create_time),
    update_time_new = FROM_UNIXTIME(update_time);
ALTER TABLE staff 
    DROP COLUMN create_time,
    DROP COLUMN update_time;
ALTER TABLE staff 
    CHANGE COLUMN create_time_new create_time datetime DEFAULT CURRENT_TIMESTAMP,
    CHANGE COLUMN update_time_new update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;