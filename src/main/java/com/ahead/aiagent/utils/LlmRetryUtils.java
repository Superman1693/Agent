package com.ahead.aiagent.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 修复 O2：LLM 调用的指数退避重试。
 *
 * <p>针对上游限流（429）与瞬时 5xx / 连接抖动做有限次重试，避免上游一次抖动
 * 就直接反映为「执行过程中出现异常」。连续失败后由调用方决定降级行为（如返回空列表）。</p>
 *
 * <p>刻意不引入 Resilience4j 等整套熔断组件：本场景只需「重试 + 退避」，
 * 保持依赖精简；若后续需要熔断/舱壁，再引入即可。</p>
 */
@Slf4j
public final class LlmRetryUtils {

    private LlmRetryUtils() {
    }

    /**
     * 带指数退避重试地执行一次可恢复的 LLM 调用。
     *
     * @param action          实际调用（通常包住 chatClient...call()）
     * @param maxAttempts     最大尝试次数（含首次），<1 视为 1
     * @param initialBackoffMs 首次退避毫秒，下限 50ms
     * @param maxBackoffMs    单次退避上限毫秒
     * @param scene           场景名，仅用于日志定位
     */
    public static <T> T callWithRetry(Supplier<T> action,
                                      int maxAttempts,
                                      long initialBackoffMs,
                                      long maxBackoffMs,
                                      String scene) {
        int attempts = Math.max(1, maxAttempts);
        long backoff = Math.max(50L, initialBackoffMs);
        long cap = Math.max(backoff, maxBackoffMs);

        RuntimeException last = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                last = e;
                boolean retryable = isRetryable(e);
                if (!retryable || i == attempts) {
                    log.warn("LLM 调用失败（场景={}，第 {}/{} 次，可重试={}）: {}",
                            scene, i, attempts, retryable, e.getMessage());
                    break;
                }
                long sleep = Math.min(cap, backoff) + ThreadLocalRandom.current().nextLong(0, 100);
                log.warn("LLM 调用失败，{}ms 后重试（场景={}，第 {}/{} 次）: {}",
                        sleep, scene, i, attempts, e.getMessage());
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                backoff = Math.min(cap, backoff * 2);
            }
        }
        throw last == null ? new IllegalStateException("LLM 调用失败：未执行任何尝试") : last;
    }

    /** 判断异常链中是否出现可重试特征（限流 / 网关错误 / 连接抖动 / 超时） */
    private static boolean isRetryable(Throwable e) {
        Throwable current = e;
        int depth = 0;
        while (current != null && depth++ < 6) {
            String msg = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (msg.contains("429") || msg.contains("rate limit") || msg.contains("too many requests")
                    || msg.contains("timeout") || msg.contains("timed out")
                    || msg.contains("502") || msg.contains("503") || msg.contains("504")
                    || msg.contains("service unavailable") || msg.contains("connection reset")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
