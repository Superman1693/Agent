-- 热门推荐表
CREATE TABLE IF NOT EXISTS recommendation (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    title         VARCHAR(200)  NOT NULL COMMENT '标题',
    description   TEXT          NOT NULL COMMENT '描述',
    image_url     VARCHAR(512)  NOT NULL COMMENT '图片URL',
    price         DECIMAL(10,2) NOT NULL COMMENT '价格',
    rating        DECIMAL(3,2)  NOT NULL COMMENT '评分(0-5)',
    review_count  INT           NOT NULL DEFAULT 0 COMMENT '评价数',
    tags          JSON          NOT NULL COMMENT '标签数组',
    location      VARCHAR(200)  COMMENT '位置信息',
    score         DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '热门分数',
    source        VARCHAR(100)  COMMENT '数据来源',
    is_active     TINYINT       NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_active_score (is_active, score DESC),
    INDEX idx_update_time (update_time DESC),
    INDEX idx_location (location)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热门推荐表';
