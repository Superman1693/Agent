package com.ahead.aiagent.model.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.List;

@Data
public class ItineraryPlaceUpdateRequest implements Serializable {

    @Min(value = 1, message = "天数至少为1")
    private Integer dayNumber;

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "时间格式必须为 HH:mm")
    private String time;

    private String name;

    @Pattern(regexp = "^(attraction|restaurant|hotel)$", message = "类型只能是 attraction, restaurant, hotel")
    private String type;

    private String imageUrl;

    private String distance;

    private String duration;

    private List<String> tags;

    private static final long serialVersionUID = 1L;
}
