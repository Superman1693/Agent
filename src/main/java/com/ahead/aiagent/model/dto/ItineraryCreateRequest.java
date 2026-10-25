package com.ahead.aiagent.model.dto;

import lombok.Data;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
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

    /** 功能7：人均预算（元），可选 */
    @DecimalMin(value = "0.00", message = "人均预算不能为负数")
    private BigDecimal budgetPerPerson;

    private static final long serialVersionUID = 1L;
}
