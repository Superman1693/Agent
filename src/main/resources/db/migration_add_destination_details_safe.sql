-- 为destination表添加详情字段的安全迁移脚本
-- 使用存储过程检查字段是否存在，避免重复添加

DELIMITER $$

-- 创建添加字段的存储过程
DROP PROCEDURE IF EXISTS add_column_if_not_exists$$
CREATE PROCEDURE add_column_if_not_exists(
    IN tableName VARCHAR(128),
    IN columnName VARCHAR(128),
    IN columnDefinition VARCHAR(512)
)
BEGIN
    DECLARE column_count INT;
    
    SELECT COUNT(*) INTO column_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = tableName
      AND COLUMN_NAME = columnName;
    
    IF column_count = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', tableName, ' ADD COLUMN ', columnName, ' ', columnDefinition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- 添加字段
CALL add_column_if_not_exists('destination', 'location', "VARCHAR(200) COMMENT '具体位置' AFTER country");
CALL add_column_if_not_exists('destination', 'rating', "DECIMAL(3,2) COMMENT '评分(0-5)' AFTER is_active");
CALL add_column_if_not_exists('destination', 'review_count', "INT COMMENT '评价数' AFTER rating");
CALL add_column_if_not_exists('destination', 'duration', "VARCHAR(50) COMMENT '建议游玩时长' AFTER review_count");
CALL add_column_if_not_exists('destination', 'group_size', "INT COMMENT '适合团队人数' AFTER duration");
CALL add_column_if_not_exists('destination', 'difficulty', "VARCHAR(20) COMMENT '难度等级' AFTER group_size");
CALL add_column_if_not_exists('destination', 'price', "DECIMAL(10,2) COMMENT '人均预算' AFTER difficulty");

-- 删除存储过程
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
