package com.ahead.aiagent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 热门推荐分页视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationPageVO {

    private List<RecommendationVO> list;

    private Long total;

    private Integer page;

    private Integer size;
}
