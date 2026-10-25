package com.ahead.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("itinerary")
public class Itinerary implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer travelers;

    private String destination;

    private String coverImage;

    private String status;

    /** 出发前提醒是否已发送: 0未发送 1已发送（配合 ItineraryReminderJob） */
    private Integer reminderSent;

    /** 分享令牌（32位随机hex），NULL 表示未开启分享 */
    private String shareToken;

    /** 人均预算（元），可空 */
    private BigDecimal budgetPerPerson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
