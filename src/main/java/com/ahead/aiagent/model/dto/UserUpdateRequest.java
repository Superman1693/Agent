package com.ahead.aiagent.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {

    @Size(max = 128, message = "邮箱长度不能超过128")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 修改密码时需同时传 oldPassword */
    @Size(min = 6, max = 32, message = "新密码长度 6-32")
    private String newPassword;

    @Size(min = 6, max = 32, message = "旧密码长度 6-32")
    private String oldPassword;
}
