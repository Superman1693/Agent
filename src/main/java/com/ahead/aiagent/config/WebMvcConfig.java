package com.ahead.aiagent.config;

import com.ahead.aiagent.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    /**
     * 安全修复 E17/D4：不再使用 allowedOriginPatterns("*") + allowCredentials(true) 的组合。
     * 允许来源通过 app.cors.allowed-origins 配置（逗号分隔），默认仅本地开发地址。
     */
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://127.0.0.1:3000,http://127.0.0.1:5173}")
    private String allowedOrigins;

    // 将 CORS 配置统一在此处，避免多个 WebMvcConfigurer 实现类冲突
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        registry.addMapping("/**")
                .allowCredentials(true)
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*")
                // SSE 长连接需要较长的 max age，减少预检请求频率
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/ai/**", "/files/**", "/apikey/**", "/user/me", "/user/update", "/user/avatar",
                "/itinerary/**", "/order/**", "/favorite/**", "/api/**", "/notification/**",
                // 安全修复 E4/E5/D2/D3：昂贵的刷新任务与会写库的 hotdata 接口必须鉴权
                "/destination/refresh", "/recommendation/refresh", "/recommendation/hotdata")
                // 功能5：行程分享查看为公开端点（按 token 鉴权，无需登录；保留 context-path 变体，仿现有写法）
                .excludePathPatterns("/user/register", "/user/login", "/api/user/register", "/api/user/login",
                        "/itinerary/share/**", "/api/itinerary/share/**");
    }
}
