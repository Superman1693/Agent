package com.ahead.aiagent.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetRequest {

    @Size(min = 3, max = 32, message = "用户名长度 3-32")
    private String username;

    @Size(max = 128, message = "邮箱长度不能超过128")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度 6-32")
    private String newPassword;
}