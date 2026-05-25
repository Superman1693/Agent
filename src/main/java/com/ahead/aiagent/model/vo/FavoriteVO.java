package com.ahead.aiagent.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FavoriteVO {

    private Long id;

    private Long targetId;

    private String targetType;

    private String targetName;

    private String targetImage;

    private LocalDateTime createTime;
}
