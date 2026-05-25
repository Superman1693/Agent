package com.ahead.aiagent.controller;

import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.common.ResultUtils;
import com.ahead.aiagent.model.vo.LocationResolveVO;
import com.ahead.aiagent.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/reverse")
    @Operation(summary = "基于经纬度逆地理编码（高德地图MCP）")
    public BaseResponse<LocationResolveVO> reverseGeocode(Double latitude, Double longitude) {
        try {
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
}
