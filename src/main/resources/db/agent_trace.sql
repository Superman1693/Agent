-- 修复 O23：Agent 过程回溯（完整 trace 落库）
--
-- 背景：
--   前端收到的 thought 事件内容被摘要化（>120 字截断、HTML/JSON 折叠），完整过程不落地，
--   既不利于用户「展开详情」理解 Agent 决策，也不利于线上排障。
--
-- 使用方式：
--   执行本脚本建表后，把配置 app.trace.persist-enabled 置为 true 即启用落库。
--   应用侧默认关闭（false），未建表时不会有任何写入尝试，不影响现有功能。
--
-- 查询接口：GET /api/ai/trace?runId=manus_xxxx

CREATE TABLE IF NOT EXISTS agent_trace (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    run_id      VARCHAR(64)  NOT NULL COMMENT '任务运行 ID（manus_xxx）',
    user_id     BIGINT       NULL COMMENT '用户 ID',
    step        INT          NOT NULL DEFAULT 0 COMMENT '步骤序号',
    event_type  VARCHAR(32)  NOT NULL COMMENT '事件类型：thought/tool_call/tool_result/artifact/final/done/error',
    content     MEDIUMTEXT   NULL COMMENT '事件完整内容（不截断）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (id),
    KEY idx_run_id (run_id),
    KEY idx_user_time (user_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Agent 过程回溯记录';
