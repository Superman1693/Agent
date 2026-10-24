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

    /**
     * 修复 O24：数据来源标注。
     * llm = 由大模型生成（价格/评分/评价数均为估算值，仅供参考）；api = 第三方接口校验；manual = 人工录入。
     */
    private String dataSource;

    private List<String> tags;
}
