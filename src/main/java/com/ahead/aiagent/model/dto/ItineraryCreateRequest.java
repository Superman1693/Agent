package com.ahead.aiagent.model.dto;

import lombok.Data;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;

@Data
public class ItineraryCreateRequest implements Serializable {

    @NotBlank(message = "标题不能为空")
    private String title;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    @NotNull(message = "旅行人数不能为空")
    @Min(value = 1, message = "旅行人数至少为1")
    private Integer travelers;

    @NotBlank(message = "目的地不能为空")
    private String destination;

    private static final long serialVersionUID = 1L;
}
