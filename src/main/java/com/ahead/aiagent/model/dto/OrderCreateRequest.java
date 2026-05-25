package com.ahead.aiagent.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

@Data
public class OrderCreateRequest implements Serializable {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotBlank(message = "商品类型不能为空")
    private String productType;

    @NotNull(message = "出行人数不能为空")
    @Min(value = 1, message = "出行人数至少为1")
    private Integer travelers;

    @NotNull(message = "出发日期不能为空")
    private LocalDate startDate;

    @NotBlank(message = "联系人不能为空")
    private String contactName;

    @NotBlank(message = "联系电话不能为空")
    private String contactPhone;

    private static final long serialVersionUID = 1L;
}
