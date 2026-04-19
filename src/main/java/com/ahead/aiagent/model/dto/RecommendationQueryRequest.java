package com.ahead.aiagent.model.dto;

import lombok.Data;

/**
 * 热门推荐查询请求
 */
@Data
public class RecommendationQueryRequest {

    /**
     * 页码（从1开始）
     */
    private Integer page = 1;

    /**
     * 每页条数
     */
    private Integer size = 10;

    /**
     * 排序字段（score/updateTime）
     */
    private String sortBy = "score";

    /**
     * 排序方向（asc/desc）
     */
    private String sortOrder = "desc";
}
