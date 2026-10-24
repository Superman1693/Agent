package com.ahead.aiagent.common;

import lombok.Getter;

/**
 * 错误码
 */
@Getter
public enum ErrorCode {
    SUCCESS(0, "ok", ""),
    PARAMS_ERROR(40000, "请求参数错误", ""),
    PARAMS_VALIDATION_ERROR(40001, "参数校验失败", ""),
    // 修复 E29：原与 PARAMS_VALIDATION_ERROR 重复使用 40001，导致调用方无法区分「参数为空」与「参数校验失败」
    NULL_ERROR(40002, "请求参数为空", ""),
    NOT_LOGIN(40100, "未登录", ""),
    NO_AUTH_ERROR(40101, "无权限", ""),
    LOGIN_FAILED(40200, "登录失败", ""),
    FORBIDDEN_ERROR(40300, "禁止访问", ""),
    // 修复 E29/D21：原为 40404，与 api.md 文档约定的 40400 不一致，统一为 40400
    NOT_FOUND_ERROR(40400, "数据不存在", ""),
    SYSTEM_ERROR(50000, "系统内部异常", "");

    private final int code;
    /** 状态码信息 */
    private final String message;
    /** 状态码描述 */
    private final String description;

    ErrorCode(int code, String message, String description) {
        this.code = code;
        this.message = message;
        this.description = description;
    }
}
