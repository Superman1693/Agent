package com.ahead.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "itinerary_place", autoResultMap = true)
public class ItineraryPlace implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long itineraryId;

    private Integer dayNumber;

    private String time;

    private String name;

    private String type;

    private String imageUrl;

    private String distance;

    private String duration;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
