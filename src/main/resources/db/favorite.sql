CREATE TABLE IF NOT EXISTS favorite (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL COMMENT '用户ID',
    target_id     BIGINT       NOT NULL COMMENT '收藏目标ID',
    target_type   VARCHAR(30)  NOT NULL COMMENT '收藏类型: destination/recommendation',
    target_name   VARCHAR(200) NOT NULL COMMENT '目标名称快照',
    target_image  VARCHAR(512) COMMENT '目标图片快照',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_user_target (user_id, target_id, target_type),
    INDEX idx_user_type_time (user_id, target_type, create_time DESC),
    INDEX idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';
