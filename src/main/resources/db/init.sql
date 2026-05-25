-- MySQL 版本的初始化脚本

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(128) NOT NULL,
    avatar_url  VARCHAR(512),
    email       VARCHAR(128),
    role        VARCHAR(16)  NOT NULL DEFAULT 'user' COMMENT 'user / admin',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 0=禁用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- API Key 表
CREATE TABLE IF NOT EXISTS api_key (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    api_key     VARCHAR(64)  NOT NULL UNIQUE,
    name        VARCHAR(64)  COMMENT '备注名称',
    total_quota BIGINT       NOT NULL DEFAULT 100 COMMENT '总配额（调用次数）',
    used_quota  BIGINT       NOT NULL DEFAULT 0 COMMENT '已用配额',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_api_key (api_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Key表';

-- 精选目的地表
CREATE TABLE IF NOT EXISTS destination (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(128)  NOT NULL,
    country         VARCHAR(128)  NOT NULL,
    location        VARCHAR(200)  COMMENT '具体位置',
    image_url       VARCHAR(512)  NOT NULL,
    description     VARCHAR(200)  NOT NULL,
    tag             VARCHAR(20)   NOT NULL,
    tag_color       ENUM('primary', 'secondary', 'tertiary') NOT NULL,
    score           DECIMAL(5,2)  NOT NULL DEFAULT 0.00 COMMENT '排序用',
    source_url      TEXT COMMENT '来源链接',
    source_keyword  VARCHAR(128) COMMENT '搜索词',
    is_active       TINYINT       NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    rating          DECIMAL(3,2)  COMMENT '评分(0-5)',
    review_count    INT           COMMENT '评价数',
    duration        VARCHAR(50)   COMMENT '建议游玩时长',
    group_size      INT           COMMENT '适合团队人数',
    difficulty      VARCHAR(20)   COMMENT '难度等级',
    price           DECIMAL(10,2) COMMENT '人均预算',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_name_country (name, country),
    INDEX idx_active_score (is_active, score DESC),
    INDEX idx_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='精选目的地表';

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

-- 订单表
CREATE TABLE IF NOT EXISTS travel_order (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL COMMENT '用户ID',
    order_no       VARCHAR(40)  NOT NULL COMMENT '订单号',
    product_id     BIGINT       NOT NULL COMMENT '商品ID',
    product_type   VARCHAR(20)  NOT NULL COMMENT '商品类型: destination/package',
    product_name   VARCHAR(200) NOT NULL COMMENT '商品名称',
    product_image  VARCHAR(512) COMMENT '商品封面',
    total_amount   DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '订单金额',
    travelers      INT          NOT NULL COMMENT '出行人数',
    start_date     DATE         NOT NULL COMMENT '出发日期',
    contact_name   VARCHAR(64)  NOT NULL COMMENT '联系人',
    contact_phone  VARCHAR(32)  NOT NULL COMMENT '联系电话',
    status         VARCHAR(30)  NOT NULL DEFAULT 'pending_payment' COMMENT '状态: pending_payment/pending_travel/completed/refund',
    cancel_reason  VARCHAR(200) COMMENT '取消原因',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_order_no (order_no),
    INDEX idx_order_user (user_id, create_time DESC),
    INDEX idx_order_status (status),
    INDEX idx_order_product (product_type, product_id),
    CONSTRAINT chk_order_travelers CHECK (travelers >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 收藏表
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
