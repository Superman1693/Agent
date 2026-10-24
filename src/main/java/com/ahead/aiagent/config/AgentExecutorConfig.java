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

    /**
     * 修复 O1（隔离）：目的地/推荐定时刷新专用线程池。
     *
     * <p>刷新任务含外部 HTTP（搜索/LLM/图片）且可并行多个关键词，此前与 Agent SSE 共用
     * {@code agentTaskExecutor}，高峰期会互相挤占队列导致 SSE 任务排队延迟。</p>
     *
     * <p>容量刻意小于 Agent 池（core 2 / max 4 / 队列 8），配合 CallerRuns：
     * 队列满时由定时任务线程自己执行，天然反压，不会堆积过多外部 HTTP 调用。</p>
     */
    @Bean(name = "refreshTaskExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor refreshTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("refresh-");
        executor.setKeepAliveSeconds(60);
        // 优雅停机：等待在途刷新任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 修复 O1（隔离）：Agent 过程回溯（trace）异步落库专用小线程池。
     *
     * <p>与刷新池分开的原因：落库是纯 JDBC 小写入（毫秒级），而刷新任务含长外部 HTTP；
     * 混用会让 trace 写入排在刷新后面、SSE 过程数据延迟可见。拒绝策略用 Discard：
     * trace 属可丢弃的观测数据，宁可丢一条也不阻塞/反压 SSE 主链路（落库失败本就有自动熔断）。</p>
     */
    @Bean(name = "traceTaskExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor traceTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("trace-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
