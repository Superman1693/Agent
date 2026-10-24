package com.ahead.aiagent.service;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 找回密码邮箱验证码（安全修复 E19/D6）。
 * 单机内存实现：TTL 10 分钟、60 秒发送冷却、最多 5 次校验尝试，摘要存储不落明文。
 * 多实例部署时应替换为 Redis 实现（SET NX EX + 原子重试计数）。
 */
@Slf4j
@Service
public class VerificationCodeService {

    private static final long TTL_MILLIS = 10 * 60_000L;
    private static final long RESEND_COOLDOWN_MILLIS = 60_000L;
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private record CodeEntry(String codeHash, long expireAt, int attempts) {
    }

    private final Map<String, CodeEntry> store = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSendAt = new ConcurrentHashMap<>();

    /** 是否允许向该 key 重新发送（60 秒冷却）。 */
    public boolean canSend(String key) {
        Long last = lastSendAt.get(key);
        return last == null || System.currentTimeMillis() - last >= RESEND_COOLDOWN_MILLIS;
    }

    /** 生成并记录验证码（仅存 SHA-256 摘要），返回验证码明文用于发送邮件。 */
    public String issue(String key) {
        String code = cn.hutool.core.util.RandomUtil.randomNumbers(6);
        store.put(key, new CodeEntry(DigestUtil.sha256Hex(code), System.currentTimeMillis() + TTL_MILLIS, 0));
        lastSendAt.put(key, System.currentTimeMillis());
        return code;
    }

    /**
     * 校验验证码。成功后立即失效；失败累加尝试次数，超过上限直接作废。
     */
    public boolean verify(String key, String code) {
        CodeEntry entry = store.get(key);
        if (entry == null || System.currentTimeMillis() > entry.expireAt()) {
            store.remove(key);
            return false;
        }
        if (entry.attempts() >= MAX_VERIFY_ATTEMPTS) {
            store.remove(key);
            return false;
        }
        if (!MessageDigest.isEqual(entry.codeHash().getBytes(), DigestUtil.sha256Hex(code).getBytes())) {
            store.put(key, new CodeEntry(entry.codeHash(), entry.expireAt(), entry.attempts() + 1));
            return false;
        }
        store.remove(key);
        return true;
    }
}
