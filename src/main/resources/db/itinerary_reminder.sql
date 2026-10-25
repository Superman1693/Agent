-- 行程出发前提醒（功能：通知生产方补齐）
-- ItineraryReminderJob 每天 8 点扫描 startDate = 今天+3天 且 reminder_sent=0 的行程，
-- 发送提醒通知后置 reminder_sent=1（列默认 0，存量行程自动视为未提醒，历史行程由日期条件天然过滤）。

ALTER TABLE itinerary
    ADD COLUMN reminder_sent TINYINT NOT NULL DEFAULT 0 COMMENT '出发前提醒是否已发送: 0未发送 1已发送';

-- 提醒扫描查询条件索引（起始日期 + 未提醒标记）
ALTER TABLE itinerary
    ADD INDEX idx_itinerary_remind (start_date, reminder_sent);
