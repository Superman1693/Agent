-- 行程预算（功能7）
-- 人均预算由创建/更新入参或 AI 文本提取写入；estimatedBudget 为展示层估算值（人均 × 人数），不落库。

ALTER TABLE itinerary
    ADD COLUMN budget_per_person DECIMAL(10,2) NULL COMMENT '人均预算（元），NULL 表示未设置';
