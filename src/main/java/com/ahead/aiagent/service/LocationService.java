package com.ahead.aiagent.service;

import com.ahead.aiagent.model.vo.LocationResolveVO;
import com.ahead.aiagent.model.vo.WeatherVO;

/**
 * 位置服务
 */
public interface LocationService {

    /**
     * 基于经纬度逆地理编码（高德地图 MCP）
     */
    LocationResolveVO reverseGeocodeByAmapMcp(Double latitude, Double longitude);

    /**
     * 按城市名查询天气（修复 E45/O19：后端代理 open-meteo，避免前端直连第三方）
     *
     * @param city 城市名
     * @param date 目标日期 yyyy-MM-dd，为空表示当前天气
     */
    WeatherVO getWeather(String city, String date);
}
