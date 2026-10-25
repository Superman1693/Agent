package com.ahead.aiagent.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 功能6：新增/更新目的地评价请求（同用户同目的地幂等更新）
 */
@Data
public class DestinationReviewSaveRequest implements Serializable {

    @NotNull(message = "目的地ID不能为空")
    private Long destinationId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分范围为 1-5")
    @Max(value = 5, message = "评分范围为 1-5")
    private Integer rating;

    @Size(max = 500, message = "评价内容不能超过 500 字")
    private String content;

    private static final long serialVersionUID = 1L;
}
