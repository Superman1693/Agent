package com.ahead.aiagent.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 修复 O1：为 Agent / SSE 异步任务定义专用线程池。
 *
 * <p>此前 AiController 使用 {@code CompletableFuture.runAsync(...)}，默认落在
 * {@link java.util.concurrent.ForkJoinPool#commonPool()}：并行度 = CPU 核数 - 1，且全 JVM 共享。
 * 一次 Agent 任务耗时可达数十秒，会长时间占用公共池，挤占其它并行流（stream parallel 等）。</p>
 *
 * <p>这里改为独立线程池：核心/最大线程数可控、有界队列、明确的拒绝策略，
 * 并在线程名前缀里带上 {@code agent-task-} 便于线上排查与线程 dump 分析。</p>
 */
@Configuration
public class AgentExecutorConfig {

    @Bean(name = "agentTaskExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor agentTaskExecutor(
            @Value("${app.agent.executor.core-size:4}") int coreSize,
            @Value("${app.agent.executor.max-size:16}") int maxSize,
            @Value("${app.agent.executor.queue-capacity:100}") int queueCapacity,
            MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("agent-task-");
        executor.setKeepAliveSeconds(60);
        // 优雅停机：等待在途任务完成，避免 SSE 任务被中断后客户端悬挂
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        // 队列满且线程达上限时由调用线程执行，避免任务被静默丢弃导致 SSE 永不完结
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        // 修复 O13：暴露线程池运行指标（队列深度 / 活跃线程 / 池大小），便于容量规划与告警
        Gauge.builder("ai_agent_executor_queue_size", executor,
                e -> e.getThreadPoolExecutor().getQueue().size()).register(meterRegistry);
        Gauge.builder("ai_agent_executor_active_threads", executor,
                e -> e.getThreadPoolExecutor().getActiveCount()).register(meterRegistry);
        Gauge.builder("ai_agent_executor_pool_size", executor,
                e -> e.getThreadPoolExecutor().getPoolSize()).register(meterRegistry);
        return executor;
    }
}
