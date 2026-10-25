package com.ahead.aiagent.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ItineraryVO implements Serializable {

    private Long id;

    private String title;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private Integer travelers;

    private String destination;

    private String coverImage;

    private String status;

    /** 功能7：人均预算（元） */
    private BigDecimal budgetPerPerson;

    /** 功能7：估算总预算 = 人均预算 × 人数，任一为空为 null */
    private BigDecimal estimatedBudget;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
