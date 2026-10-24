package com.ahead.aiagent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 修复 O13：AI 链路可观测性指标。
 *
 * <p>打点项覆盖清单建议：任务成功率、平均步数、平均耗时、超步数终止率、
 * token 消耗、工具调用分布、SSE 异常断开率；线程池队列深度由
 * {@code AgentExecutorConfig} 以 Gauge 形式注册。</p>
 *
 * <p>指标经 Actuator 暴露，Prometheus 抓取地址：{@code /api/actuator/prometheus}。</p>
 */
@Component
public class AgentMetrics {

    private final MeterRegistry registry;

    private final Counter taskStarted;
    private final Counter taskSucceeded;
    private final Counter taskFailed;
    private final Counter taskTerminatedByMaxSteps;
    private final Counter sseDisconnected;
    private final Timer taskDuration;
    private final DistributionSummary stepDistribution;

    public AgentMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.taskStarted = counter("started");
        this.taskSucceeded = counter("succeeded");
        this.taskFailed = counter("failed");
        this.taskTerminatedByMaxSteps = counter("max_steps");
        this.sseDisconnected = Counter.builder("ai_agent_sse_disconnected_total")
                .description("SSE 异常断开次数")
                .register(registry);
        this.taskDuration = Timer.builder("ai_agent_task_duration")
                .description("Agent 任务耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.stepDistribution = DistributionSummary.builder("ai_agent_steps")
                .description("Agent 任务步数分布")
                .register(registry);
    }

    private Counter counter(String result) {
        return Counter.builder("ai_agent_task_total")
                .description("Agent 任务计数（按结果分类）")
                .tag("result", result)
                .register(registry);
    }

    public void taskStarted() {
        taskStarted.increment();
    }

    public void taskSucceeded(long elapsedMillis, int steps) {
        taskSucceeded.increment();
        taskDuration.record(elapsedMillis, TimeUnit.MILLISECONDS);
        if (steps > 0) {
            stepDistribution.record(steps);
        }
    }

    public void taskFailed() {
        taskFailed.increment();
    }

    public void taskTerminatedByMaxSteps() {
        taskTerminatedByMaxSteps.increment();
    }

    public void sseDisconnected() {
        sseDisconnected.increment();
    }

    /** 工具调用分布：按工具名打点 */
    public void toolCalled(String toolName) {
        String name = (toolName == null || toolName.isBlank()) ? "unknown" : toolName;
        Counter.builder("ai_agent_tool_calls_total")
                .description("工具调用分布")
                .tag("tool", name)
                .register(registry)
                .increment();
    }

    /** LLM token 消耗累计（传入增量，非正数忽略） */
    public void addTokens(long tokens) {
        if (tokens <= 0) {
            return;
        }
        Counter.builder("ai_agent_llm_tokens_total")
                .description("LLM token 消耗累计")
                .register(registry)
                .increment(tokens);
    }
}
