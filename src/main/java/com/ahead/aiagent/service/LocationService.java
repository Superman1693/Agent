package com.ahead.aiagent.service;

import com.ahead.aiagent.model.vo.LocationResolveVO;

/**
 * 位置服务
 */
public interface LocationService {

    /**
     * 基于经纬度逆地理编码（高德地图 MCP）
     */
    LocationResolveVO reverseGeocodeByAmapMcp(Double latitude, Double longitude);
}
