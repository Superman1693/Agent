package com.ahead.aiagent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 功能6：目的地评价分页 VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DestinationReviewPageVO implements Serializable {

    private List<DestinationReviewVO> list;

    private Long total;

    private Integer page;

    private Integer size;

    private static final long serialVersionUID = 1L;
}
