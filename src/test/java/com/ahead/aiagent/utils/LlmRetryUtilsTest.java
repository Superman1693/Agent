package com.ahead.aiagent.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 修复 O2 的单元测试：{@link LlmRetryUtils} 的重试与退避行为。
 *
 * <p>全部使用最小退避（50ms）以免拖慢测试；验证点包括成功短路、可重试错误重试、
 * 不可重试错误立即失败、重试耗尽后抛出最后一次异常、以及 cause 链中的特征识别。</p>
 */
class LlmRetryUtilsTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF = 50L;
    private static final long MAX_BACKOFF = 60L;

    @Test
    void returnsImmediatelyWhenActionSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        String result = LlmRetryUtils.callWithRetry(() -> {
            calls.incrementAndGet();
            return "ok";
        }, MAX_ATTEMPTS, INITIAL_BACKOFF, MAX_BACKOFF, "test.success");

        assertEquals("ok", result);
        assertEquals(1, calls.get(), "成功时不应发生重试");
    }

    @Test
    void retriesOnRetryableErrorThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        String result = LlmRetryUtils.callWithRetry(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("HTTP 429 Too Many Requests");
            }
            return "recovered";
        }, MAX_ATTEMPTS, INITIAL_BACKOFF, MAX_BACKOFF, "test.retryable");

        assertEquals("recovered", result);
        assertEquals(3, calls.get(), "应重试到第 3 次才成功");
    }

    @Test
    void doesNotRetryOnNonRetryableError() {
        AtomicInteger calls = new AtomicInteger();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> LlmRetryUtils.callWithRetry(() -> {
            calls.incrementAndGet();
            throw new RuntimeException("invalid parameter: model not found");
        }, MAX_ATTEMPTS, INITIAL_BACKOFF, MAX_BACKOFF, "test.non-retryable"));

        assertEquals("invalid parameter: model not found", ex.getMessage());
        assertEquals(1, calls.get(), "不可重试错误只应调用一次");
    }

    @Test
    void throwsLastErrorAfterExhaustingAttempts() {
        AtomicInteger calls = new AtomicInteger();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> LlmRetryUtils.callWithRetry(() -> {
            calls.incrementAndGet();
            throw new RuntimeException("HTTP 503 Service Unavailable");
        }, MAX_ATTEMPTS, INITIAL_BACKOFF, MAX_BACKOFF, "test.exhausted"));

        assertTrue(ex.getMessage().contains("503"), "应抛出最后一次的原始异常");
        assertEquals(MAX_ATTEMPTS, calls.get(), "应尝试 maxAttempts 次");
    }

    @Test
    void detectsRetryableFeatureInCauseChain() {
        AtomicInteger calls = new AtomicInteger();
        String result = LlmRetryUtils.callWithRetry(() -> {
            if (calls.incrementAndGet() < 2) {
                throw new RuntimeException("wrapper", new RuntimeException("connection reset by peer"));
            }
            return "ok";
        }, MAX_ATTEMPTS, INITIAL_BACKOFF, MAX_BACKOFF, "test.cause");

        assertEquals("ok", result);
        assertEquals(2, calls.get(), "应能从 cause 链中识别可重试特征");
    }

    @Test
    void treatsNonPositiveMaxAttemptsAsSingleAttempt() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(RuntimeException.class, () -> LlmRetryUtils.callWithRetry(() -> {
            calls.incrementAndGet();
            throw new RuntimeException("HTTP 429");
        }, 0, INITIAL_BACKOFF, MAX_BACKOFF, "test.zero"));

        assertEquals(1, calls.get(), "maxAttempts < 1 应视为 1 次");
    }
}
