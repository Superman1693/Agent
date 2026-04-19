package com.ahead.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 热门推荐实体
 */
@Data
@TableName(value = "recommendation", autoResultMap = true)
public class Recommendation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    private String imageUrl;

    private BigDecimal price;

    private BigDecimal rating;

    private Integer reviewCount;

    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private String location;

    private BigDecimal score;

    private String source;

    private Integer isActive;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
