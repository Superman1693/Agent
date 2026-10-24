package com.ahead.aiagent.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 标记通知已读请求（修复 E23）
 */
@Data
public class ReadNotificationRequest {

    /** 为空表示将当前用户全部未读通知标记为已读 */
    private List<Long> notificationIds;
}
