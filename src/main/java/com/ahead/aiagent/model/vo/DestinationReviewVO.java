package com.ahead.aiagent.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 功能6：目的地评价 VO
 */
@Data
public class DestinationReviewVO implements Serializable {

    private Long id;

    private Long destinationId;

    private Long userId;

    /** 批量联 user 表填充，避免 N+1 */
    private String username;

    /** 用户头像（user.avatar_url） */
    private String avatar;

    private Integer rating;

    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createTime;

    /** 当前请求是否为该评价作者（无登录态时为 false） */
    private Boolean mine;

    private static final long serialVersionUID = 1L;
}
