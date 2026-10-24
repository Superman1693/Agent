-- 消息通知表（修复 E23：前端调用的 /notification/** 在后端缺失对应 Controller）
CREATE TABLE IF NOT EXISTS notification (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NOT NULL COMMENT '用户ID',
    title       VARCHAR(200)  NOT NULL COMMENT '标题',
    content     VARCHAR(1000) COMMENT '内容',
    type        VARCHAR(20)   NOT NULL DEFAULT 'system' COMMENT '类型: system/promotion/reminder',
    is_read     TINYINT       NOT NULL DEFAULT 0 COMMENT '是否已读: 0未读 1已读',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_notification_user (user_id, is_read, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';
