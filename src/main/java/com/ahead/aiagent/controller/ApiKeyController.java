package com.ahead.aiagent.controller;

import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ResultUtils;
import com.ahead.aiagent.model.dto.ApiKeyCreateRequest;
import com.ahead.aiagent.model.entity.ApiKey;
import com.ahead.aiagent.security.AuthContext;
import com.ahead.aiagent.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/apikey")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    /** 创建 API Key（需要 JWT 登录） */
    @PostMapping("/create")
    public BaseResponse<ApiKey> create(@RequestBody ApiKeyCreateRequest request) {
        Long userId = AuthContext.getUserId();
        ApiKey key = apiKeyService.create(userId, request);
        return ResultUtils.success(key);
    }

    /** 查询当前用户的所有 API Key */
    @GetMapping("/list")
    public BaseResponse<List<ApiKey>> list() {
        Long userId = AuthContext.getUserId();
        return ResultUtils.success(apiKeyService.listByUser(userId));
    }

    /** 禁用指定 API Key */
    @PostMapping("/disable/{id}")
    public BaseResponse<Boolean> disable(@PathVariable Long id) {
        Long userId = AuthContext.getUserId();
        apiKeyService.disable(userId, id);
        return ResultUtils.success(true);
    }
}
