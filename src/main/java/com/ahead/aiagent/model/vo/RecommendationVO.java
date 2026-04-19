package com.ahead.aiagent.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 热门推荐视图对象
 */
@Data
public class RecommendationVO {

    private Long id;

    private String title;

    private String description;

    private String imageUrl;

    private BigDecimal price;

    private BigDecimal rating;

    private Integer reviewCount;

    private List<String> tags;
}
