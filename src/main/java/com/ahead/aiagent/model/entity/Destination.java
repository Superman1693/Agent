package com.ahead.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 精选目的地实体
 */
@Data
@TableName("destination")
public class Destination {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String country;

    private String imageUrl;

    private String description;

    private String tag;

    private String tagColor;

    private BigDecimal score;

    private String sourceUrl;

    private String sourceKeyword;

    private Integer isActive;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
