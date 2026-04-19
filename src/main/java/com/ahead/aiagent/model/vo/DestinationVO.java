package com.ahead.aiagent.model.vo;

import lombok.Data;

/**
 * 目的地视图对象
 */
@Data
public class DestinationVO {

    private Long id;

    private String name;

    private String country;

    private String imageUrl;

    private String description;

    private String tag;

    private String tagColor;
}
