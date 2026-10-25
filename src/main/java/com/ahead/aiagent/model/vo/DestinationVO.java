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

    /**
     * 修复 O24：数据来源标注。
     * llm = 由大模型生成（价格/评分/评价数均为估算值，仅供参考）；api = 第三方接口校验；manual = 人工录入。
     */
    private String dataSource;

    /** 功能6：用户评价均分（1 位小数），无评价为 null（仅目的地详情聚合） */
    private BigDecimal userAvgRating;

    /** 功能6：用户评价条数，无评价为 null（仅目的地详情聚合） */
    private Long userReviewCount;

    private List<String> tags;

    private List<HighlightVO> highlights;

    @Data
    public static class HighlightVO {
        private String icon;
        private String title;
        private String description;
    }
}
