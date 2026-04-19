package com.ahead.aiagent.service.impl;

import cn.hutool.core.util.RandomUtil;
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
        key.setApiKey("ak-" + RandomUtil.randomString(32));
        key.setName(request.getName());
        key.setTotalQuota(request.getTotalQuota() != null ? request.getTotalQuota() : 100L);
        key.setUsedQuota(0L);
        key.setStatus(1);
        apiKeyMapper.insert(key);
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
        ApiKey record = apiKeyMapper.selectOne(
                new LambdaQueryWrapper<ApiKey>().eq(ApiKey::getApiKey, apiKey));
        if (record == null || record.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无效的 API Key");
        }
        if (record.getUsedQuota() >= record.getTotalQuota()) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "API Key 配额已耗尽");
        }
        int updated = apiKeyMapper.incrementUsedQuota(apiKey);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "API Key 配额已耗尽");
        }
        return record.getUserId();
    }
}
