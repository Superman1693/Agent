package com.ahead.aiagent.utils;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单的内存滑动窗口限流器。
 *
 * 用于给缺少验证码的敏感接口（如找回密码）加频率限制，缓解暴力尝试与批量探测。
 * 注意：这是单机内存实现，多实例部署时应替换为 Redis 计数器（INCR + EXPIRE）。
 */
public class SimpleRateLimiter {

    /**
     * 防御修复：key（如用户名/邮箱/IP）由客户端可控输入构造，无界 Map 会被随机 key 撑爆内存。
     * 超过上限时先清理全过期项，仍超限则拒绝新 key 的记录（已有 key 行为不变）。
     */
    private static final int MAX_KEYS = 100_000;

    private final int maxAttempts;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public SimpleRateLimiter(int maxAttempts, Duration window) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = window.toMillis();
    }

    /**
     * 记录一次访问。
     *
     * @param key 限流维度（如用户名 / 邮箱 / IP）
     * @return true 表示允许本次访问；false 表示已超出窗口内允许次数
     */
    public boolean tryAcquire(String key) {
        if (key == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (!hits.containsKey(key) && hits.size() >= MAX_KEYS && !evictExpired(now)) {
            // 内存保护：满载且无法清理时，未登记的新 key 直接放行不计次（宁可少限流不可撑爆内存）
            return true;
        }
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > windowMillis) {
                deque.pollFirst();
            }
            if (deque.size() >= maxAttempts) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    /** 清理全部已过期/空的 key，返回清理后是否已低于上限。 */
    private boolean evictExpired(long now) {
        hits.values().removeIf(deque -> {
            synchronized (deque) {
                while (!deque.isEmpty() && now - deque.peekFirst() > windowMillis) {
                    deque.pollFirst();
                }
                return deque.isEmpty();
            }
        });
        return hits.size() < MAX_KEYS;
    }
}
