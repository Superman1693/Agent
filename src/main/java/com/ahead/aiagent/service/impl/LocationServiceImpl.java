package com.ahead.aiagent.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ahead.aiagent.model.vo.LocationResolveVO;
import com.ahead.aiagent.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 位置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    @Value("${amap.api-key:${AMAP_MAPS_API_KEY:}}")
    private String amapApiKey;

    private static final String AMAP_REVERSE_URL = "https://restapi.amap.com/v3/geocode/regeo";

    @Override
    public LocationResolveVO reverseGeocodeByAmapMcp(Double latitude, Double longitude) {
        if (StrUtil.isBlank(amapApiKey)) {
            throw new RuntimeException("缺少高德 API Key，请配置 amap.api-key 或 AMAP_MAPS_API_KEY");
        }

        String location = longitude + "," + latitude;
        String response = HttpUtil.get(AMAP_REVERSE_URL +
                "?key=" + amapApiKey +
                "&location=" + location +
                "&extensions=base&radius=1000&batch=false&roadlevel=0");

        JSONObject root = JSONUtil.parseObj(response);
        String status = root.getStr("status");
        if (!"1".equals(status)) {
            throw new RuntimeException("高德逆地理查询失败: " + root.getStr("info", "unknown"));
        }

        JSONObject regeo = root.getJSONObject("regeocode");
        if (regeo == null) {
            throw new RuntimeException("高德逆地理返回为空");
        }
        JSONObject component = regeo.getJSONObject("addressComponent");

        String province = component == null ? null : firstNonBlank(component.getStr("province"));
        String city = extractCity(component, province);
        String district = component == null ? null : firstNonBlank(component.getStr("district"));
        String formattedAddress = firstNonBlank(regeo.getStr("formatted_address"));

        String displayText = buildDisplayText(province, city, district, formattedAddress);
        String queryText = buildQueryText(province, city, district, displayText);

        if (StrUtil.isBlank(queryText)) {
            throw new RuntimeException("高德定位解析结果为空");
        }

        return new LocationResolveVO(
                displayText,
                queryText,
                StrUtil.blankToDefault(province, ""),
                StrUtil.blankToDefault(city, ""),
                StrUtil.blankToDefault(district, ""),
                StrUtil.blankToDefault(formattedAddress, ""),
                latitude,
                longitude,
                "amap-rest");
    }

    private String extractCity(JSONObject component, String province) {
        if (component == null) {
            return null;
        }
        Object cityObj = component.get("city");
        if (cityObj instanceof String cityStr) {
            String city = firstNonBlank(cityStr);
            if (StrUtil.isNotBlank(city)) {
                return city;
            }
        }
        // 直辖市场景 city 可能为空，回退到 province
        return firstNonBlank(province, component.getStr("district"));
    }

    private String buildDisplayText(String province, String city, String district, String formattedAddress) {
        if (StrUtil.isNotBlank(province) && StrUtil.isNotBlank(city)) {
            return province + " " + city;
        }
        if (StrUtil.isNotBlank(province)) {
            return province;
        }
        if (StrUtil.isNotBlank(city)) {
            return city;
        }
        if (StrUtil.isNotBlank(district)) {
            return district;
        }
        if (StrUtil.isNotBlank(formattedAddress)) {
            return formattedAddress;
        }
        return "未知位置";
    }

    private String buildQueryText(String province, String city, String district, String fallback) {
        if (StrUtil.isNotBlank(province) && StrUtil.isNotBlank(city)) {
            return province + city;
        }
        if (StrUtil.isNotBlank(city)) {
            return city;
        }
        if (StrUtil.isNotBlank(province)) {
            return province;
        }
        if (StrUtil.isNotBlank(district)) {
            return district;
        }
        return fallback;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
