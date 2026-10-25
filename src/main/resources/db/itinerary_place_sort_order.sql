-- 行程地点排序（功能4：同一天内支持自定义顺序）
-- 展示顺序规则：ORDER BY day_number ASC, sort_order ASC, id ASC

ALTER TABLE itinerary_place
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0 COMMENT '同一天内展示顺序，越小越靠前';

-- 存量初始化：按 (itinerary_id, day_number, id) 用 MySQL 8 窗口函数 ROW_NUMBER 编号（从 0 开始）
UPDATE itinerary_place ip
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY itinerary_id, day_number ORDER BY id) - 1 AS rn
    FROM itinerary_place
) ranked ON ip.id = ranked.id
SET ip.sort_order = ranked.rn;
