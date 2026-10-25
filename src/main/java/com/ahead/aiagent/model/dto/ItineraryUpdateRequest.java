package com.ahead.aiagent.model.dto;

import lombok.Data;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ItineraryUpdateRequest implements Serializable {

    private String title;

    private LocalDate startDate;

    private LocalDate endDate;

    @Min(value = 1, message = "旅行人数至少为1")
    private Integer travelers;

    private String destination;

    /** 功能7：人均预算（元），可选 */
    @DecimalMin(value = "0.00", message = "人均预算不能为负数")
    private BigDecimal budgetPerPerson;

    private static final long serialVersionUID = 1L;
}
