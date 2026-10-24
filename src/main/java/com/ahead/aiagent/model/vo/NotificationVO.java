package com.ahead.aiagent.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知 VO（修复 E23）
 */
@Data
public class NotificationVO {

    private Long id;

    private String title;

    private String content;

    /** system / promotion / reminder */
    private String type;

    private Boolean isRead;

    private LocalDateTime createTime;
}
