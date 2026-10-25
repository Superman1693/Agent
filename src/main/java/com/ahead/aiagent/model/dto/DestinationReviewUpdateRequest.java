package com.ahead.aiagent.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 功能6：按评价 ID 更新请求
 */
@Data
public class DestinationReviewUpdateRequest implements Serializable {

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分范围为 1-5")
    @Max(value = 5, message = "评分范围为 1-5")
    private Integer rating;

    @Size(max = 500, message = "评价内容不能超过 500 字")
    private String content;

    private static final long serialVersionUID = 1L;
}
