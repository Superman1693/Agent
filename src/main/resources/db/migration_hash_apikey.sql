-- 迁移：API Key 哈希存储（安全修复 E21/D7）
-- 背景：api_key 列原先存明文 key，数据库泄露即泄露全部凭证；现改为只存 SHA-256 摘要。
-- 执行方式：手工执行（Flyway 默认关闭）；如启用 Flyway，请改放到 db/migration/ 下并命名 V*__*.sql。

ALTER TABLE api_key
    ADD COLUMN api_key_prefix VARCHAR(16) NULL COMMENT '明文 key 前 10 位（仅用于列表掩码展示）' AFTER api_key;

-- 重要提示：
-- 1. 本迁移不回填 api_key_prefix（存量记录已无法从哈希还原明文前缀），列表掩码将显示 ****。
-- 2. 存量明文 key 不会自动哈希：升级后原 key 校验（对输入求哈希后比对摘要）必然失败，
--    即存量明文 key 全部失效，请各用户在「API Key 管理」页重建 Key。
-- 3. 如需平滑过渡，可在升级前通知用户重建；重建后新 key 仅在创建响应中出现一次，请妥善保存。
