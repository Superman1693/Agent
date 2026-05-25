package com.ahead.aiagent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class FavoriteAddRequest implements Serializable {

    @NotNull(message = "收藏目标ID不能为空")
    private Long targetId;

    @NotBlank(message = "收藏类型不能为空")
    private String targetType;

    private static final long serialVersionUID = 1L;
}
