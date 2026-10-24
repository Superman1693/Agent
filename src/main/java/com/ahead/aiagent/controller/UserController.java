package com.ahead.aiagent.controller;

import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.common.ResultUtils;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.model.dto.PasswordResetCodeRequest;
import com.ahead.aiagent.model.dto.PasswordResetRequest;
import com.ahead.aiagent.model.dto.UserLoginRequest;
import com.ahead.aiagent.model.dto.UserRegisterRequest;
import com.ahead.aiagent.model.dto.UserUpdateRequest;
import com.ahead.aiagent.model.vo.LoginVO;
import com.ahead.aiagent.model.vo.UserVO;
import com.ahead.aiagent.security.AuthContext;
import com.ahead.aiagent.service.UserService;
import com.ahead.aiagent.utils.AliyunOSSOperator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final AliyunOSSOperator ossOperator;

    /** 注册（无需认证） */
    @PostMapping("/register")
    public BaseResponse<Long> register(@Valid @RequestBody UserRegisterRequest request) {
        return ResultUtils.success(userService.register(request));
    }

    /** 登录（无需认证） */
    @PostMapping("/login")
    public BaseResponse<LoginVO> login(@Valid @RequestBody UserLoginRequest request) {
        return ResultUtils.success(userService.login(request));
    }

    /** 发送找回密码验证码（无需认证；安全修复 E19/D6） */
    @PostMapping("/send-code")
    public BaseResponse<Void> sendCode(@Valid @RequestBody PasswordResetCodeRequest request,
                                       HttpServletRequest httpRequest) {
        userService.sendCode(request, resolveClientIp(httpRequest));
        return ResultUtils.success(null);
    }

    /** 找回密码：需携带邮箱验证码（无需认证；安全修复 E19/D6） */
    @PostMapping("/reset-password")
    public BaseResponse<UserVO> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        return ResultUtils.success(userService.resetPassword(request));
    }

    /** 获取当前登录用户信息（需认证） */
    @GetMapping("/me")
    public BaseResponse<UserVO> me() {
        return ResultUtils.success(userService.getUserInfo(AuthContext.getUserId()));
    }

    /** 修改用户信息：邮箱 / 密码（需认证） */
    @PutMapping("/update")
    public BaseResponse<UserVO> update(@Valid @RequestBody UserUpdateRequest request) {
        return ResultUtils.success(userService.updateInfo(AuthContext.getUserId(), request));
    }

    /**
     * 上传头像到阿里云 OSS（需认证）
     * 请求类型：multipart/form-data，参数名：file
     */
    @PostMapping("/avatar")
    public BaseResponse<UserVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        Long userId = AuthContext.getUserId();
        log.info("用户 {} 上传头像, originalName={}", userId, file.getOriginalFilename());
        String url = ossOperator.upload(file, "avatar/");
        return ResultUtils.success(userService.updateAvatar(userId, url));
    }

    /** 提取调用方真实 IP（优先 X-Forwarded-For 首段，回退 remoteAddr），用于验证码发送限流 */
    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && ip.contains(",")) {
            ip = ip.substring(0, ip.indexOf(',')).trim();
        }
        return (ip == null || ip.isEmpty()) ? request.getRemoteAddr() : ip;
    }
}
