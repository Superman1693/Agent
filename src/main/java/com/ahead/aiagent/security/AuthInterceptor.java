package com.ahead.aiagent.security;

import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.model.entity.ApiKey;
import com.ahead.aiagent.service.ApiKeyService;
import com.ahead.aiagent.service.UserService;
import com.ahead.aiagent.model.entity.User;
import com.ahead.aiagent.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final ApiKeyService apiKeyService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // OPTIONS 预检请求直接放行，由 CORS 配置处理
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 文件下载允许通过临时 token 进行匿名访问，token 的真实性由下载接口再次校验
        String requestUri = request.getRequestURI();
        String downloadToken = request.getParameter("token");
        if (requestUri != null
                && requestUri.startsWith("/api/files/")
                && requestUri.endsWith("/download")
                && StringUtils.hasText(downloadToken)) {
            return true;
        }

        // 1. 优先尝试 API Key 认证（Header: X-Api-Key 或 Query: apiKey）
        String apiKey = request.getHeader("X-Api-Key");
        if (!StringUtils.hasText(apiKey)) {
            apiKey = request.getParameter("apiKey");
        }
        if (StringUtils.hasText(apiKey)) {
            Long userId = apiKeyService.validateAndConsume(apiKey);
            AuthContext.set(userId, "apikey");
            return true;
        }

        // 2. JWT Bearer Token 认证
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Claims claims = jwtUtils.parseToken(token); // 过期/无效会抛 JwtException，由全局异常处理
            Long userId = Long.valueOf(claims.getSubject());
            String role = claims.get("role", String.class);
            AuthContext.set(userId, role);
            return true;
        }

        throw new BusinessException(ErrorCode.NOT_LOGIN, "请先登录或提供有效的 API Key");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        AuthContext.clear();
    }
}
