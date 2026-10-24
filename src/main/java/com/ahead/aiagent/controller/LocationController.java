package com.ahead.aiagent.controller;

import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.common.ResultUtils;
import com.ahead.aiagent.model.vo.LocationResolveVO;
import com.ahead.aiagent.model.vo.WeatherVO;
import com.ahead.aiagent.service.LocationService;
import com.ahead.aiagent.utils.SimpleRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 位置解析控制器
 */
@Slf4j
@RestController
@RequestMapping("/location")
@RequiredArgsConstructor
@Tag(name = "位置服务", description = "位置解析相关接口")
public class LocationController {

    private final LocationService locationService;

    /**
     * 安全修复：/location/** 匿名可调但会消耗高德/open-meteo 第三方配额，按 IP 加限流。
     */
    private final SimpleRateLimiter rateLimiter = new SimpleRateLimiter(30, Duration.ofMinutes(1));

    private boolean rateLimited(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && ip.contains(",")) {
            ip = ip.substring(0, ip.indexOf(',')).trim();
        }
        return !rateLimiter.tryAcquire(ip == null ? request.getRemoteAddr() : ip);
    }

    @GetMapping("/reverse")
    @Operation(summary = "基于经纬度逆地理编码（高德地图MCP）")
    public BaseResponse<LocationResolveVO> reverseGeocode(Double latitude, Double longitude, HttpServletRequest request) {
        try {
            if (rateLimited(request)) {
                return ResultUtils.error(ErrorCode.FORBIDDEN_ERROR, "请求过于频繁，请稍后重试", "");
            }
            if (latitude == null || longitude == null) {
                return ResultUtils.error(ErrorCode.PARAMS_ERROR, "经纬度参数不能为空", "");
            }
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                return ResultUtils.error(ErrorCode.PARAMS_ERROR, "经纬度参数不合法", "");
            }

            LocationResolveVO result = locationService.reverseGeocodeByAmapMcp(latitude, longitude);
            return ResultUtils.success(result);
        } catch (Exception e) {
            log.error("逆地理编码失败", e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "位置解析失败，请稍后重试", "");
        }
    }

    @GetMapping("/weather")
    @Operation(summary = "按城市查询天气（后端代理 open-meteo）")
    public BaseResponse<WeatherVO> weather(@RequestParam String city,
                                           @RequestParam(required = false) String date,
                                           HttpServletRequest request) {
        try {
            if (rateLimited(request)) {
                return ResultUtils.error(ErrorCode.FORBIDDEN_ERROR, "请求过于频繁，请稍后重试", "");
            }
            if (cn.hutool.core.util.StrUtil.isBlank(city)) {
                return ResultUtils.error(ErrorCode.PARAMS_ERROR, "城市不能为空", "");
            }
            if (date != null && !date.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                return ResultUtils.error(ErrorCode.PARAMS_ERROR, "date 参数格式应为 yyyy-MM-dd", "");
            }
            WeatherVO result = locationService.getWeather(city, date);
            if (result == null) {
                return ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "未查询到天气信息", "");
            }
            return ResultUtils.success(result);
        } catch (Exception e) {
            log.error("查询天气失败", e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "天气查询失败，请稍后重试", "");
        }
    }
}
