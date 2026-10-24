package com.ahead.aiagent.service;

import com.ahead.aiagent.model.dto.PasswordResetCodeRequest;
import com.ahead.aiagent.model.dto.PasswordResetRequest;
import com.ahead.aiagent.model.dto.UserLoginRequest;
import com.ahead.aiagent.model.dto.UserRegisterRequest;
import com.ahead.aiagent.model.dto.UserUpdateRequest;
import com.ahead.aiagent.model.entity.User;
import com.ahead.aiagent.model.vo.LoginVO;
import com.ahead.aiagent.model.vo.UserVO;

public interface UserService {

    Long register(UserRegisterRequest request);

    LoginVO login(UserLoginRequest request);

    User getById(Long id);

    UserVO getUserInfo(Long userId);

    UserVO updateInfo(Long userId, UserUpdateRequest request);

    UserVO resetPassword(PasswordResetRequest request);

    UserVO updateAvatar(Long userId, String avatarUrl);

    /** 安全修复 E19/D6：发送找回密码邮箱验证码（username+email 必须匹配已注册用户） */
    void sendCode(PasswordResetCodeRequest request, String clientIp);
}
