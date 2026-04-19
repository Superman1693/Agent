package com.ahead.aiagent.service;

import com.ahead.aiagent.model.dto.ApiKeyCreateRequest;
import com.ahead.aiagent.model.entity.ApiKey;

import java.util.List;

public interface ApiKeyService {

    ApiKey create(Long userId, ApiKeyCreateRequest request);

    List<ApiKey> listByUser(Long userId);

    void disable(Long userId, Long keyId);

    /**
     * 校验 API Key 是否有效并扣减配额，返回对应 userId
     * 无效或超限时抛出 BusinessException
     */
    Long validateAndConsume(String apiKey);
}
