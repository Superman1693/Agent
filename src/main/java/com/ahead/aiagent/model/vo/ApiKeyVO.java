package com.ahead.aiagent.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Key 视图对象。
 * 安全修复 E21/D7：列表接口不再回传明文 apiKey，仅返回掩码后的 key。
 */
@Data
public class ApiKeyVO {

    private Long id;

    private String name;

    /** 掩码后的 key，例如 ak-abcd****wxyz */
    private String maskedKey;

    private Long totalQuota;

    private Long usedQuota;

    private Integer status;

    private LocalDateTime createTime;
}
