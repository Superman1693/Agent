package com.ahead.aiagent.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 综合搜索结果项 VO（修复 E23）
 */
@Data
public class SearchResultVO {

    private Long id;

    /** destination / recommendation */
    private String type;

    private String name;

    private String description;

    private String imageUrl;

    private BigDecimal price;
}
