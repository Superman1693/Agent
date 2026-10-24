package com.ahead.aiagent.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 找回密码：发送邮箱验证码请求（安全修复 E19/D6）
 */
@Data
public class PasswordResetCodeRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度 3-32")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Size(max = 128, message = "邮箱长度不能超过128")
    @Email(message = "邮箱格式不正确")
    private String email;
}
