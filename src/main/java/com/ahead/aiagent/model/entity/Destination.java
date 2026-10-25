package com.ahead.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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

    /** 功能6：数据来源 llm/api/manual（迁移见 db/destination_review.sql，存量默认 llm） */
    private String dataSource;

    private Integer isActive;

    // 详情字段
    private BigDecimal rating;

    private Integer reviewCount;

    private String duration;

    private Integer groupSize;

    private String difficulty;

    private BigDecimal price;

    private String location;

    // 修复 E41：补上自动填充标记，使 MybatisPlusConfig 的 MetaObjectHandler 生效
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
