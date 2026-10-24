package com.ahead.aiagent.service.impl;

import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.mapper.UserMapper;
import com.ahead.aiagent.model.dto.PasswordResetRequest;
import com.ahead.aiagent.model.dto.UserLoginRequest;
import com.ahead.aiagent.model.dto.UserRegisterRequest;
import com.ahead.aiagent.model.dto.UserUpdateRequest;
import com.ahead.aiagent.model.entity.User;
import com.ahead.aiagent.model.vo.LoginVO;
import com.ahead.aiagent.model.vo.UserVO;
import com.ahead.aiagent.service.UserService;
import com.ahead.aiagent.utils.JwtUtils;
import com.ahead.aiagent.utils.PasswordUtils;
import com.ahead.aiagent.utils.SimpleRateLimiter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;

    /** 安全修复 E19/D6：找回密码无验证码，增加频率限制（同一标识 5 分钟内最多 3 次） */
    private final SimpleRateLimiter resetLimiter = new SimpleRateLimiter(3, Duration.ofMinutes(5));

    @Override
    public Long register(UserRegisterRequest request) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名已存在");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(PasswordUtils.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRole("user");
        user.setStatus(1);
        userMapper.insert(user);
        return user.getId();
    }

    /**
     * 登录
     * @param request
     * @return
     */
    @Override
    public LoginVO login(UserLoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (user == null || !PasswordUtils.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "用户名或密码错误");
        }
        // 修复 E39：user.getStatus() 为 null 时直接 != 1 会拆箱 NPE
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "账号已被禁用");
        }
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUser(toVO(user));
        return loginVO;
    }

    @Override
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public UserVO getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        return toVO(user);
    }

    /**
     * 修改用户信息
     * @param userId
     * @param request
     * @return
     */
    @Override
    public UserVO updateInfo(Long userId, UserUpdateRequest request) {
        // 修改密码：需要验证旧密码
        if (StringUtils.hasText(request.getNewPassword())) {
            if (!StringUtils.hasText(request.getOldPassword())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "修改密码需要提供旧密码");
            }
            User current = userMapper.selectById(userId);
            // 修复 E40：用户记录不存在时 current 为 null，直接 getPassword() 会 NPE
            if (current == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
            }
            if (!PasswordUtils.matches(request.getOldPassword(), current.getPassword())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "旧密码不正确");
            }
        }

        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
        if (StringUtils.hasText(request.getEmail())) {
            wrapper.set(User::getEmail, request.getEmail());
        }
        if (StringUtils.hasText(request.getNewPassword())) {
            wrapper.set(User::getPassword, PasswordUtils.encode(request.getNewPassword()));
        }
        userMapper.update(null, wrapper);
        return getUserInfo(userId);
    }
    /**
     * 重置密码
     * @param request
     * @return
     */
    @Override
    public UserVO resetPassword(PasswordResetRequest request) {
        boolean hasUsername = StringUtils.hasText(request.getUsername());
        boolean hasEmail = StringUtils.hasText(request.getEmail());
        if (!hasUsername && !hasEmail) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请填写用户名或邮箱中的至少一项");
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>();
        if (hasUsername) {
            wrapper.eq(User::getUsername, request.getUsername().trim());
        }
        if (hasEmail) {
            wrapper.eq(User::getEmail, request.getEmail().trim());
        }

        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到匹配的用户，请检查用户名或邮箱");
        }

        userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, user.getId())
                        .set(User::getPassword, PasswordUtils.encode(request.getNewPassword())));

        return getUserInfo(user.getId());
    }

    /**
     * 修改头像
     * @param userId
     * @param avatarUrl
     * @return
     */
    @Override
    public UserVO resetPassword(PasswordResetRequest request) {
        boolean hasUsername = StringUtils.hasText(request.getUsername());
        boolean hasEmail = StringUtils.hasText(request.getEmail());
        if (!hasUsername && !hasEmail) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请填写用户名或邮箱中的至少一项");
        }

        // 安全修复 E19/D6：无验证码的找回密码接口，加频率限制，缓解暴力尝试与批量探测
        String limitKey = (hasUsername ? request.getUsername().trim() : "")
                + "|" + (hasEmail ? request.getEmail().trim() : "");
        if (!resetLimiter.tryAcquire(limitKey)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "操作过于频繁，请 5 分钟后再试");
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>();
        if (hasUsername) {
            wrapper.eq(User::getUsername, request.getUsername().trim());
        }
        if (hasEmail) {
            wrapper.eq(User::getEmail, request.getEmail().trim());
        }

        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到匹配的用户，请检查用户名或邮箱");
        }

        userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, user.getId())
                        .set(User::getPassword, PasswordUtils.encode(request.getNewPassword())));

        return getUserInfo(user.getId());
    }

    @Override
    public UserVO updateAvatar(Long userId, String avatarUrl) {
        userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getAvatarUrl, avatarUrl));
        return getUserInfo(userId);
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setRole(user.getRole());
        return vo;
    }
}
