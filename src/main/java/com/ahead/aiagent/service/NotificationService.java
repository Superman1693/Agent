package com.ahead.aiagent.service;

import com.ahead.aiagent.model.vo.NotificationListVO;

import java.util.List;

/**
 * 消息通知服务（修复 E23）
 */
public interface NotificationService {

    NotificationListVO listNotifications(boolean unreadOnly, Integer page, Integer size, Long userId);

    boolean markRead(List<Long> notificationIds, Long userId);
}
