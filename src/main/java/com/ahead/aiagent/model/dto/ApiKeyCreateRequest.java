package com.ahead.aiagent.model.dto;

import lombok.Data;

@Data
public class ApiKeyCreateRequest {

    /** 备注名称 */
    private String name;

    /** 总配额，默认 100 */
    private Long totalQuota = 100L;
}
