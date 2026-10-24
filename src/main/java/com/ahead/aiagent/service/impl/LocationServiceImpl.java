package com.ahead.aiagent.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.model.vo.LocationResolveVO;
import com.ahead.aiagent.model.vo.WeatherVO;
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
    private static final String OPEN_METEO_GEO_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast";

    /**
     * 按城市名查询天气（修复 E45/O19：由后端代理 open-meteo，前端不再直连第三方，
     * 统一鉴权/限流/错误处理，也避免暴露用户 IP）。
     */
    @Override
    public WeatherVO getWeather(String city, String date) {
        if (StrUtil.isBlank(city)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "城市不能为空");
        }
        try {
            String geoUrl = OPEN_METEO_GEO_URL + "?name=" + java.net.URLEncoder.encode(city.trim(), java.nio.charset.StandardCharsets.UTF_8)
                    + "&count=1&language=zh&format=json";
            JSONObject geo = JSONUtil.parseObj(HttpUtil.get(geoUrl));
            JSONArray results = geo.getJSONArray("results");
            if (results == null || results.isEmpty()) {
                return null;
            }
            JSONObject hit = results.getJSONObject(0);
            Double lat = hit.getDouble("latitude");
            Double lon = hit.getDouble("longitude");
            if (lat == null || lon == null) {
                return null;
            }

            WeatherVO vo = new WeatherVO();
            if (StrUtil.isNotBlank(date)) {
                String url = OPEN_METEO_FORECAST_URL + "?latitude=" + lat + "&longitude=" + lon
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto"
                        + "&start_date=" + date + "&end_date=" + date;
                JSONObject resp = JSONUtil.parseObj(HttpUtil.get(url));
                JSONObject daily = resp.getJSONObject("daily");
                if (daily == null) {
                    return null;
                }
                Integer code = firstOf(daily.getJSONArray("weather_code"));
                Integer max = firstOf(daily.getJSONArray("temperature_2m_max"));
                Integer min = firstOf(daily.getJSONArray("temperature_2m_min"));
                if (code == null || max == null || min == null) {
                    return null;
                }
                vo.setWeatherCode(code);
                vo.setMaxTemperature(max);
                vo.setMinTemperature(min);
                vo.setTemperature(Math.round((max + min) / 2.0f));
                vo.setTargetDate(date);
            } else {
                String url = OPEN_METEO_FORECAST_URL + "?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code&timezone=auto";
                JSONObject resp = JSONUtil.parseObj(HttpUtil.get(url));
                JSONObject current = resp.getJSONObject("current");
                if (current == null) {
                    return null;
                }
                Double temp = current.getDouble("temperature_2m");
                Integer code = current.getInt("weather_code");
                if (temp == null || code == null) {
                    return null;
                }
                vo.setTemperature(Math.round(temp.floatValue()));
                vo.setWeatherCode(code);
            }
            return vo;
        } catch (Exception e) {
            log.error("查询天气失败: city={}, date={}", city, date, e);
            return null;
        }
    }

    private Integer firstOf(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return null;
        }
        return array.getInt(0);
    }

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
