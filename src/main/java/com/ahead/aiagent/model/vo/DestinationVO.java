package com.ahead.aiagent.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 目的地视图对象
 */
@Data
public class DestinationVO {

    private Long id;

    private String name;

    private String country;

    private String location;

    private String imageUrl;

    private String description;

    private String tag;

    private String tagColor;

    private BigDecimal rating;

    private Integer reviewCount;

    private String duration;

    private Integer groupSize;

    private String difficulty;

    private BigDecimal price;

    private List<String> tags;

    private List<HighlightVO> highlights;

    @Data
    public static class HighlightVO {
        private String icon;
        private String title;
        private String description;
    }
}
