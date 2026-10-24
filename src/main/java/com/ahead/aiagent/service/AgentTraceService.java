package com.ahead.aiagent.service;

import com.ahead.aiagent.model.vo.AgentTraceVO;

import java.util.List;

/**
 * Agent 过程回溯服务（修复 O23）
 */
public interface AgentTraceService {

    /**
     * 异步记录一条 Agent 事件（完整内容）。默认不落库，需开启 app.trace.persist-enabled。
     */
    void recordAsync(String runId, Long userId, int step, String eventType, String content);

    /**
     * 按 runId 查询完整过程（仅返回属于该用户或公共的记录）。
     */
    List<AgentTraceVO> listByRunId(String runId, Long userId);
}
