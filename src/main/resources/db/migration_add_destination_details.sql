-- 为destination表添加详情字段的迁移脚本
-- 注意：如果字段已存在会报错，可以忽略或手动检查

-- 添加location字段
ALTER TABLE destination ADD COLUMN location VARCHAR(200) COMMENT '具体位置' AFTER country;

-- 添加详情字段
ALTER TABLE destination ADD COLUMN rating DECIMAL(3,2) COMMENT '评分(0-5)' AFTER is_active;
ALTER TABLE destination ADD COLUMN review_count INT COMMENT '评价数' AFTER rating;
ALTER TABLE destination ADD COLUMN duration VARCHAR(50) COMMENT '建议游玩时长' AFTER review_count;
ALTER TABLE destination ADD COLUMN group_size INT COMMENT '适合团队人数' AFTER duration;
ALTER TABLE destination ADD COLUMN difficulty VARCHAR(20) COMMENT '难度等级' AFTER group_size;
ALTER TABLE destination ADD COLUMN price DECIMAL(10,2) COMMENT '人均预算' AFTER difficulty;
