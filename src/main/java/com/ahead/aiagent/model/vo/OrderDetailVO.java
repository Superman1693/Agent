package com.ahead.aiagent.model.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class OrderDetailVO {

    private Long orderId;

    private String orderNo;

    private String productName;

    private String productImage;

    private java.math.BigDecimal totalAmount;

    private String status;

    private java.time.LocalDateTime createTime;

    private Integer travelers;

    private LocalDate startDate;

    private String contactName;

    private String contactPhone;

    private String cancelReason;
}
