package com.ahead.aiagent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 综合搜索列表 VO（修复 E23）
 */
@Data
@AllArgsConstructor
public class SearchListVO {

    private List<SearchResultVO> list;

    private long total;
}
