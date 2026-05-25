-- 行程表
CREATE TABLE IF NOT EXISTS itinerary (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL COMMENT '用户ID',
    title        VARCHAR(200) NOT NULL COMMENT '行程标题',
    start_date   DATE         NOT NULL COMMENT '开始日期',
    end_date     DATE         NOT NULL COMMENT '结束日期',
    travelers    INT          NOT NULL COMMENT '旅行人数',
    destination  VARCHAR(200) NOT NULL COMMENT '目的地',
    cover_image  VARCHAR(512) COMMENT '封面图片',
    status       VARCHAR(20)  NOT NULL DEFAULT 'upcoming' COMMENT '状态: upcoming, ongoing, completed',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time DESC),
    CONSTRAINT chk_dates CHECK (start_date <= end_date),
    CONSTRAINT chk_travelers CHECK (travelers >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程表';

-- 行程项表
CREATE TABLE IF NOT EXISTS itinerary_place (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    itinerary_id   BIGINT       NOT NULL COMMENT '行程ID',
    day_number     INT          NOT NULL COMMENT '第几天',
    time           VARCHAR(5)   NOT NULL COMMENT '时间 HH:mm',
    name           VARCHAR(200) NOT NULL COMMENT '地点名称',
    type           VARCHAR(20)  NOT NULL COMMENT '类型: attraction, restaurant, hotel',
    image_url      VARCHAR(512) COMMENT '图片URL',
    distance       VARCHAR(100) COMMENT '距离信息',
    duration       VARCHAR(100) COMMENT '持续时间',
    tags           JSON         COMMENT '标签数组',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_itinerary_id (itinerary_id),
    INDEX idx_day_time (itinerary_id, day_number, time),
    CONSTRAINT chk_day_number CHECK (day_number >= 1),
    CONSTRAINT chk_type CHECK (type IN ('attraction', 'restaurant', 'hotel')),
    FOREIGN KEY (itinerary_id) REFERENCES itinerary(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程项表';
