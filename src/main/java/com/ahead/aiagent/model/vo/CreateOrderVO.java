package com.ahead.aiagent.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateOrderVO {

    private Long orderId;

    private String orderNo;

    private BigDecimal totalAmount;

    private String status;

    private LocalDateTime createTime;
}
