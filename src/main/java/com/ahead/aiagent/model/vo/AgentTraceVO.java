package com.ahead.aiagent.model.vo;

import lombok.Data;

/**
 * Agent 过程回溯视图对象（修复 O23）
 */
@Data
public class AgentTraceVO {

    private Long id;

    private String runId;

    private Integer step;

    private String eventType;

    /** 完整内容（不截断） */
    private String content;

    private String createTime;
}
