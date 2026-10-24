package com.ahead.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("api_key")
public class ApiKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String apiKey;

    /** 安全修复 E21/D7：明文 key 仅创建时展示一次，库里另存前缀用于列表掩码展示 */
    private String apiKeyPrefix;

    private String name;

    /** 总配额（调用次数） */
    private Long totalQuota;

    /** 已用配额 */
    private Long usedQuota;

    /** 1=启用 0=禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
