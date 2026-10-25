-- 行程分享（功能5）
-- POST /itinerary/{id}/share 生成 32 位随机 hex 令牌；
-- GET /itinerary/share/{token} 免登录按令牌查看行程详情。

ALTER TABLE itinerary
    ADD COLUMN share_token VARCHAR(64) NULL COMMENT '分享令牌（32位随机hex），NULL 表示未开启分享';

-- 唯一索引：MySQL 唯一索引允许多个 NULL，未开启分享的行程互不冲突
ALTER TABLE itinerary
    ADD UNIQUE INDEX uk_itinerary_share_token (share_token);
