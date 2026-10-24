package com.ahead.aiagent.controller;

import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.common.ResultUtils;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.model.dto.ReadNotificationRequest;
import com.ahead.aiagent.model.vo.NotificationListVO;
import com.ahead.aiagent.security.AuthContext;
import com.ahead.aiagent.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息通知控制器（修复 E23：前端调用的 /notification/** 此前在后端缺失）
 */
@Tag(name = "消息通知")
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "获取通知列表")
    @GetMapping("/list")
    public BaseResponse<NotificationListVO> list(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        return ResultUtils.success(notificationService.listNotifications(unreadOnly, page, size, userId));
    }

    @Operation(summary = "标记通知已读（notificationIds 为空表示全部已读）")
    @PostMapping("/read")
    public BaseResponse<Boolean> read(@RequestBody(required = false) ReadNotificationRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        boolean result = notificationService.markRead(request == null ? null : request.getNotificationIds(), userId);
        return ResultUtils.success(result);
    }
}
