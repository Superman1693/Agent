package com.ahead.aiagent.controller;

import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ResultUtils;
import com.ahead.aiagent.model.dto.ApiKeyCreateRequest;
import com.ahead.aiagent.model.entity.ApiKey;
import com.ahead.aiagent.model.vo.ApiKeyVO;
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

    /** 创建 API Key（仅允许 JWT 登录调用，见 AuthInterceptor 修复 E20/D8） */
    @PostMapping("/create")
    public BaseResponse<ApiKey> create(@RequestBody ApiKeyCreateRequest request) {
        Long userId = AuthContext.getUserId();
        ApiKey key = apiKeyService.create(userId, request);
        return ResultUtils.success(key);
    }

    /** 查询当前用户的所有 API Key（安全修复 E21/D7：只返回掩码，不回传明文） */
    @GetMapping("/list")
    public BaseResponse<List<ApiKeyVO>> list() {
        Long userId = AuthContext.getUserId();
        List<ApiKeyVO> result = apiKeyService.listByUser(userId).stream()
                .map(ApiKeyController::toMaskedVO)
                .toList();
        return ResultUtils.success(result);
    }

    /** 禁用指定 API Key */
    @PostMapping("/disable/{id}")
    public BaseResponse<Boolean> disable(@PathVariable Long id) {
        Long userId = AuthContext.getUserId();
        apiKeyService.disable(userId, id);
        return ResultUtils.success(true);
    }

    private static ApiKeyVO toMaskedVO(ApiKey key) {
        ApiKeyVO vo = new ApiKeyVO();
        vo.setId(key.getId());
        vo.setName(key.getName());
        vo.setMaskedKey(maskKey(key.getApiKey()));
        vo.setTotalQuota(key.getTotalQuota());
        vo.setUsedQuota(key.getUsedQuota());
        vo.setStatus(key.getStatus());
        vo.setCreateTime(key.getCreateTime());
        return vo;
    }

    private static String maskKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
