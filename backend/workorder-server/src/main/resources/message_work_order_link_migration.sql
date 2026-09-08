USE wos;

-- 历史消息不回填关联关系；仅新产生的消息写入 work_order_id。
ALTER TABLE message
    ADD COLUMN work_order_id BIGINT NULL COMMENT '关联工单id' AFTER sender_id,
    ADD INDEX idx_message_work_order_id (work_order_id);
