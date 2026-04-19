package com.ahead.aiagent.service;

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

    UserVO updateAvatar(Long userId, String avatarUrl);
}
