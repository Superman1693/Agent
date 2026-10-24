package com.ahead.aiagent.service.impl;

import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.mapper.UserMapper;
import com.ahead.aiagent.model.dto.PasswordResetCodeRequest;
import com.ahead.aiagent.model.dto.PasswordResetRequest;
import com.ahead.aiagent.model.dto.UserLoginRequest;
import com.ahead.aiagent.model.dto.UserRegisterRequest;
import com.ahead.aiagent.model.dto.UserUpdateRequest;
import com.ahead.aiagent.model.entity.User;
import com.ahead.aiagent.model.vo.LoginVO;
import com.ahead.aiagent.model.vo.UserVO;
import com.ahead.aiagent.service.EmailService;
import com.ahead.aiagent.service.UserService;
import com.ahead.aiagent.service.VerificationCodeService;
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
    private final VerificationCodeService verificationCodeService;
    private final EmailService emailService;

    /** 安全修复 E19/D6：找回密码无验证码，增加频率限制（同一标识 5 分钟内最多 3 次） */
    private final SimpleRateLimiter resetLimiter = new SimpleRateLimiter(3, Duration.ofMinutes(5));

    /** 安全修复 E19/D6：发送验证码限流（同 IP+邮箱 5 分钟内最多 3 次），缓解邮箱轰炸 */
    private final SimpleRateLimiter sendCodeLimiter = new SimpleRateLimiter(3, Duration.ofMinutes(5));

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
     * 重置密码（安全修复 E19/D6：合并新旧两份副本，统一走验证码校验）
     * @param request
     * @return
     */
    @Override
    public UserVO resetPassword(PasswordResetRequest request) {
        // 验证码必须先校验通过：verify 成功后立即失效（删除验证码），失败抛参数错误
        String codeKey = request.getUsername().trim() + "|" + request.getEmail().trim();
        if (!StringUtils.hasText(request.getCode())
                || !verificationCodeService.verify(codeKey, request.getCode().trim())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码无效或已过期，请重新获取");
        }

        // 频率限制：缓解绕过验证码冷却的暴力尝试（同一标识 5 分钟内最多 3 次）
        if (!resetLimiter.tryAcquire(codeKey)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "操作过于频繁，请 5 分钟后再试");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername().trim())
                .eq(User::getEmail, request.getEmail().trim()));
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
     * 发送找回密码邮箱验证码（安全修复 E19/D6）
     * @param request 用户名+邮箱
     * @param clientIp 调用方 IP（限流维度之一）
     */
    @Override
    public void sendCode(PasswordResetCodeRequest request, String clientIp) {
        // 限流：同 IP+邮箱 5 分钟内最多 3 次
        String limitKey = (clientIp == null ? "unknown" : clientIp) + "|" + request.getEmail().trim();
        if (!sendCodeLimiter.tryAcquire(limitKey)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "发送过于频繁，请 5 分钟后再试");
        }

        // 用户名+邮箱必须匹配同一条用户记录；不区分哪个字段错，避免泄露账号存在性
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername().trim())
                .eq(User::getEmail, request.getEmail().trim()));
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户名与邮箱不匹配");
        }

        String codeKey = request.getUsername().trim() + "|" + request.getEmail().trim();
        // 60 秒发送冷却（复用 VerificationCodeService 的发送记录）
        if (!verificationCodeService.canSend(codeKey)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "验证码发送过于频繁，请 1 分钟后再试");
        }

        String code = verificationCodeService.issue(codeKey);
        emailService.sendVerificationCode(user.getEmail(), code);
    }

    /**
     * 修改头像
     * @param userId
     * @param avatarUrl
     * @return
     */
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
