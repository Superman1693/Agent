package com.ahead.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 修复 O23：Agent 过程回溯记录。
 * 与 agent_trace 表对应，默认不落库（见 app.trace.persist-enabled）。
 */
@Data
@TableName("agent_trace")
public class AgentTrace {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务运行 ID（manus_xxx） */
    private String runId;

    private Long userId;

    private Integer step;

    /** thought / tool_call / tool_result / artifact / final / done / error */
    private String eventType;

    /** 事件完整内容（不截断） */
    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
