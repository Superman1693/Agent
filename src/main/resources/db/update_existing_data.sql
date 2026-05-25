-- 为现有的destination数据填充默认值

UPDATE destination 
SET 
    location = CONCAT(country, '地区'),
    rating = 4.5,
    review_count = 1000,
    duration = '3-5天',
    group_size = 20,
    difficulty = '中等',
    price = 5000
WHERE location IS NULL;
