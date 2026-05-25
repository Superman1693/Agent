package com.ahead.aiagent.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderVO {

    private Long orderId;

    private String orderNo;

    private String productName;

    private String productImage;

    private BigDecimal totalAmount;

    private String status;

    private LocalDateTime createTime;
}
