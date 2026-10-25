package com.ahead.aiagent.controller;

import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.common.ResultUtils;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.model.dto.DestinationReviewSaveRequest;
import com.ahead.aiagent.model.dto.DestinationReviewUpdateRequest;
import com.ahead.aiagent.model.vo.DestinationReviewPageVO;
import com.ahead.aiagent.model.vo.DestinationReviewVO;
import com.ahead.aiagent.security.AuthContext;
import com.ahead.aiagent.service.DestinationReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 功能6：目的地用户评价控制器
 * 说明：/destination/** 未纳入 AuthInterceptor 拦截（仅 /destination/refresh 在拦截列表），
 * 因此写接口在控制器层显式校验登录态；GET /destination/review/list 为公开接口。
 */
@Tag(name = "目的地评价")
@RestController
@RequestMapping("/destination/review")
@RequiredArgsConstructor
public class DestinationReviewController {

    private final DestinationReviewService destinationReviewService;

    @Operation(summary = "新增/更新评价（同用户同目的地幂等）")
    @PostMapping
    public BaseResponse<DestinationReviewVO> saveReview(@Valid @RequestBody DestinationReviewSaveRequest request) {
        Long userId = requireLogin();
        DestinationReviewVO vo = destinationReviewService.saveReview(request, userId);
        return ResultUtils.success(vo, "ok");
    }

    @Operation(summary = "按评价 ID 更新（仅本人）")
    @PutMapping("/{id}")
    public BaseResponse<DestinationReviewVO> updateReview(
            @PathVariable Long id,
            @Valid @RequestBody DestinationReviewUpdateRequest request) {
        Long userId = requireLogin();
        DestinationReviewVO vo = destinationReviewService.updateReview(id, request, userId);
        return ResultUtils.success(vo, "ok");
    }

    @Operation(summary = "按评价 ID 删除（仅本人）")
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> deleteReview(@PathVariable Long id) {
        Long userId = requireLogin();
        boolean result = destinationReviewService.deleteReview(id, userId);
        return ResultUtils.success(result, "ok");
    }

    @Operation(summary = "分页查询目的地评价（公开）")
    @GetMapping("/list")
    public BaseResponse<DestinationReviewPageVO> listReviews(
            @RequestParam Long destinationId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        // AuthContext 有值即登录态（API Key 或 JWT），无值视为匿名，mine 置 false
        Long userId = AuthContext.getUserId();
        DestinationReviewPageVO vo = destinationReviewService.listReviews(destinationId, page, size, userId);
        return ResultUtils.success(vo);
    }

    /** 写接口登录态校验（该路径不在拦截器，控制器层兜底） */
    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        return userId;
    }
}
