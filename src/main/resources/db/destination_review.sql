-- 目的地用户评价（功能6）+ 目的地数据来源字段（功能6附带，参照 db/data_source.sql 说明落地）
-- 端点：POST/PUT/DELETE /destination/review*（登录，控制器层校验），
--       GET /destination/review/list（公开分页，mine 依赖请求是否携带有效登录态）。

CREATE TABLE IF NOT EXISTS destination_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    destination_id BIGINT NOT NULL COMMENT '目的地ID',
    rating TINYINT NOT NULL COMMENT '评分1-5',
    content VARCHAR(500) COMMENT '评价内容',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uniq_review_user_dest (user_id, destination_id),
    INDEX idx_review_dest (destination_id, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='目的地用户评价表';

-- 数据来源字段：NOT NULL DEFAULT 'llm'，ALTER 时存量行自动回填 'llm'
ALTER TABLE destination
    ADD COLUMN data_source VARCHAR(20) NOT NULL DEFAULT 'llm' COMMENT '数据来源：llm/api/manual';
