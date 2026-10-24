package com.ahead.aiagent.service;

import com.ahead.aiagent.model.vo.NotificationListVO;

import java.util.List;

/**
 * 消息通知服务（修复 E23）
 */
public interface NotificationService {

    NotificationListVO listNotifications(boolean unreadOnly, Integer page, Integer size, Long userId);

    boolean markRead(List<Long> notificationIds, Long userId);

    /**
     * 修复 O22：生产方写入通知（订单状态流转等），插入一条未读通知。
     *
     * @param userId  接收用户
     * @param title   标题
     * @param content 内容（如含订单号的提示文案）
     * @param type    通知类型，如 "order" / "system"
     */
    void notify(Long userId, String title, String content, String type);
}
