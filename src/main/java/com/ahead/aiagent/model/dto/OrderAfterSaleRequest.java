package com.ahead.aiagent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class OrderAfterSaleRequest implements Serializable {

    @NotBlank(message = "售后原因不能为空")
    @Size(max = 200, message = "售后原因长度不能超过200")
    private String reason;

    private static final long serialVersionUID = 1L;
}
