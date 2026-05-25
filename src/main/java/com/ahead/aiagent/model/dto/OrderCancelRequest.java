package com.ahead.aiagent.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class OrderCancelRequest implements Serializable {

    @Size(max = 200, message = "取消原因长度不能超过200")
    private String reason;

    private static final long serialVersionUID = 1L;
}
