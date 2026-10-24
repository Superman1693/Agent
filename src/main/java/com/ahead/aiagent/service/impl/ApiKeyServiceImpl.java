package com.ahead.aiagent.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.mapper.ApiKeyMapper;
import com.ahead.aiagent.model.dto.ApiKeyCreateRequest;
import com.ahead.aiagent.model.entity.ApiKey;
import com.ahead.aiagent.service.ApiKeyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyMapper apiKeyMapper;

    @Override
    public ApiKey create(Long userId, ApiKeyCreateRequest request) {
        ApiKey key = new ApiKey();
        key.setUserId(userId);
        // 生成 "ak-" 前缀的随机 key
        String rawKey = "ak-" + RandomUtil.randomString(32);
        // 安全修复 E21/D7：库中只存 SHA-256 摘要，泄露数据库也无法还原明文 key
        key.setApiKey(DigestUtil.sha256Hex(rawKey));
        // 明文 key 仅保留前 10 个字符用于列表掩码展示
        key.setApiKeyPrefix(rawKey.length() > 10 ? rawKey.substring(0, 10) : rawKey);
        key.setName(request.getName());
        key.setTotalQuota(request.getTotalQuota() != null ? request.getTotalQuota() : 100L);
        key.setUsedQuota(0L);
        key.setStatus(1);
        apiKeyMapper.insert(key);
        // 创建响应仅此一次返回明文 key（实体内已落库哈希，此处仅改写返回值供前端展示）
        key.setApiKey(rawKey);
        return key;
    }

    @Override
    public List<ApiKey> listByUser(Long userId) {
        return apiKeyMapper.selectList(
                new LambdaQueryWrapper<ApiKey>().eq(ApiKey::getUserId, userId));
    }

    @Override
    public void disable(Long userId, Long keyId) {
        int rows = apiKeyMapper.update(null,
                new LambdaUpdateWrapper<ApiKey>()
                        .eq(ApiKey::getId, keyId)
                        .eq(ApiKey::getUserId, userId)
                        .set(ApiKey::getStatus, 0));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "API Key 不存在或无权操作");
        }
    }

    @Override
    public Long validateAndConsume(String apiKey) {
        // 安全修复 E21/D7：比对时先对输入 key 求哈希再查库
        String keyHash = DigestUtil.sha256Hex(apiKey);
        ApiKey record = apiKeyMapper.selectOne(
                new LambdaQueryWrapper<ApiKey>().eq(ApiKey::getApiKey, keyHash));
        // 修复 E41：status 为 null 时直接 != 1 会拆箱 NPE，统一判空后比较
        if (record == null || !Integer.valueOf(1).equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无效的 API Key");
        }
        // 修复 E41：配额字段判空后再比较，避免历史脏数据触发 NPE
        long used = record.getUsedQuota() == null ? 0L : record.getUsedQuota();
        long total = record.getTotalQuota() == null ? 0L : record.getTotalQuota();
        if (used >= total) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "API Key 配额已耗尽");
        }
        int updated = apiKeyMapper.incrementUsedQuota(keyHash);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "API Key 配额已耗尽");
        }
        return record.getUserId();
    }
}
