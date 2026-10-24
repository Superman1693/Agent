package com.ahead.aiagent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 消息通知列表 VO（修复 E23）
 */
@Data
@AllArgsConstructor
public class NotificationListVO {

    private List<NotificationVO> list;

    private long unreadCount;

    private long total;
}
