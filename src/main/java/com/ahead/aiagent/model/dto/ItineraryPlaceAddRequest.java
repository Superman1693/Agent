package com.ahead.aiagent.model.dto;

import lombok.Data;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.List;

@Data
public class ItineraryPlaceAddRequest implements Serializable {

    @NotNull(message = "天数不能为空")
    @Min(value = 1, message = "天数至少为1")
    private Integer dayNumber;

    @NotBlank(message = "时间不能为空")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "时间格式必须为 HH:mm")
    private String time;

    @NotBlank(message = "地点名称不能为空")
    private String name;

    @NotBlank(message = "类型不能为空")
    @Pattern(regexp = "^(attraction|restaurant|hotel)$", message = "类型只能是 attraction, restaurant, hotel")
    private String type;

    private String imageUrl;

    private String distance;

    private String duration;

    private List<String> tags;

    private static final long serialVersionUID = 1L;
}
