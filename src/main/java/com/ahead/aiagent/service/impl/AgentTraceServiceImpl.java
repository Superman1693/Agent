package com.ahead.aiagent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.mapper.AgentTraceMapper;
import com.ahead.aiagent.model.entity.AgentTrace;
import com.ahead.aiagent.model.vo.AgentTraceVO;
import com.ahead.aiagent.service.AgentTraceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 过程回溯实现（修复 O23）。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>默认关闭落库（{@code app.trace.persist-enabled=false}），未建表时零写入尝试，不影响现有功能；</li>
 *   <li>落库走专用线程池异步执行，不阻塞 SSE 主链路；</li>
 *   <li>一旦落库失败（如未执行建表脚本）自动熔断，避免刷日志与无效写入。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTraceServiceImpl implements AgentTraceService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AgentTraceMapper agentTraceMapper;

    /** 复用 Agent 线程池做异步落库 */
    @Resource(name = "agentTaskExecutor")
    private Executor traceExecutor;

    /** 默认关闭；执行 db/agent_trace.sql 后置为 true 启用 */
    @Value("${app.trace.persist-enabled:false}")
    private boolean persistEnabled;

    /** 落库失败后自动熔断标记 */
    private final AtomicBoolean persistDisabled = new AtomicBoolean(false);

    @Override
    public void recordAsync(String runId, Long userId, int step, String eventType, String content) {
        if (!persistEnabled || persistDisabled.get() || StrUtil.isBlank(runId)) {
            return;
        }
        traceExecutor.execute(() -> {
            try {
                AgentTrace trace = new AgentTrace();
                trace.setRunId(runId);
                trace.setUserId(userId);
                trace.setStep(step);
                trace.setEventType(eventType);
                trace.setContent(content);
                agentTraceMapper.insert(trace);
            } catch (Exception e) {
                persistDisabled.set(true);
                log.warn("Agent trace 落库失败，已自动关闭落库（请确认已执行 db/agent_trace.sql）: {}", e.getMessage());
            }
        });
    }

    @Override
    public List<AgentTraceVO> listByRunId(String runId, Long userId) {
        if (StrUtil.isBlank(runId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "runId 不能为空");
        }
        LambdaQueryWrapper<AgentTrace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTrace::getRunId, runId);
        if (userId != null) {
            wrapper.eq(AgentTrace::getUserId, userId);
        }
        wrapper.orderByAsc(AgentTrace::getStep).orderByAsc(AgentTrace::getId);

        List<AgentTrace> traces;
        try {
            traces = agentTraceMapper.selectList(wrapper);
        } catch (Exception e) {
            // 修复：未开启落库 / 未执行建表脚本时，查询接口应返回空列表而不是 500，
            // 与 recordAsync 的自动熔断策略保持一致（前端已有「未找到过程记录」兜底提示）。
            log.warn("查询 Agent trace 失败（可能未建表或未开启落库）: {}", e.getMessage());
            return List.of();
        }

        return traces.stream().map(t -> {
            AgentTraceVO vo = new AgentTraceVO();
            vo.setId(t.getId());
            vo.setRunId(t.getRunId());
            vo.setStep(t.getStep());
            vo.setEventType(t.getEventType());
            vo.setContent(t.getContent());
            vo.setCreateTime(t.getCreateTime() == null ? null : t.getCreateTime().format(TIME_FORMATTER));
            return vo;
        }).toList();
    }
}
