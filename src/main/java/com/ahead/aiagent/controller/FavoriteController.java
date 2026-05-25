package com.ahead.aiagent.controller;

import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.common.ResultUtils;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.model.dto.FavoriteAddRequest;
import com.ahead.aiagent.model.vo.FavoriteListVO;
import com.ahead.aiagent.model.vo.FavoriteVO;
import com.ahead.aiagent.security.AuthContext;
import com.ahead.aiagent.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "收藏管理")
@RestController
@RequestMapping("/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "添加收藏")
    @PostMapping("/add")
    public BaseResponse<FavoriteVO> addFavorite(@Valid @RequestBody FavoriteAddRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        FavoriteVO result = favoriteService.addFavorite(request, userId);
        return ResultUtils.success(result, "ok");
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/remove")
    public BaseResponse<Boolean> removeFavorite(
            @RequestParam Long targetId,
            @RequestParam String targetType) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        boolean result = favoriteService.removeFavorite(targetId, targetType, userId);
        return ResultUtils.success(result, "ok");
    }

    @Operation(summary = "查询收藏列表")
    @GetMapping("/list")
    public BaseResponse<FavoriteListVO> listFavorites(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        FavoriteListVO result = favoriteService.listFavorites(targetType, page, size, userId);
        return ResultUtils.success(result, "ok");
    }
}
