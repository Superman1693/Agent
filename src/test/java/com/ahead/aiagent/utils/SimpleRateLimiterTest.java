package com.ahead.aiagent.utils;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 修复 E19/D6 的单元测试：{@link SimpleRateLimiter} 的滑动窗口限流行为。
 *
 * <p>覆盖：窗口内放行到上限、超限拒绝、不同 key 相互隔离、null key 直接放行、
 * 窗口过期后自动恢复。</p>
 */
class SimpleRateLimiterTest {

    @Test
    void allowsUpToMaxAttemptsWithinWindow() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(3, Duration.ofMinutes(5));

        assertTrue(limiter.tryAcquire("user-a"));
        assertTrue(limiter.tryAcquire("user-a"));
        assertTrue(limiter.tryAcquire("user-a"));
        assertFalse(limiter.tryAcquire("user-a"), "第 4 次应被拒绝");
    }

    @Test
    void isolatesDifferentKeys() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(1, Duration.ofMinutes(5));

        assertTrue(limiter.tryAcquire("user-a"));
        assertFalse(limiter.tryAcquire("user-a"));
        assertTrue(limiter.tryAcquire("user-b"), "不同 key 之间互不影响");
    }

    @Test
    void nullKeyIsAlwaysAllowed() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(1, Duration.ofMinutes(5));

        assertTrue(limiter.tryAcquire(null));
        assertTrue(limiter.tryAcquire(null), "key 为 null 时不参与限流");
    }

    @Test
    void recoversAfterWindowExpires() throws InterruptedException {
        SimpleRateLimiter limiter = new SimpleRateLimiter(1, Duration.ofMillis(150));

        assertTrue(limiter.tryAcquire("user-c"));
        assertFalse(limiter.tryAcquire("user-c"));

        Thread.sleep(250);
        assertTrue(limiter.tryAcquire("user-c"), "窗口过期后应重新放行");
    }
}
