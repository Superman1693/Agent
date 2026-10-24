package com.ahead.aiagent.model.vo;

import lombok.Data;

/**
 * 天气信息 VO（修复 E45/O19：由后端代理第三方天气 API，前端不再直连 open-meteo）
 */
@Data
public class WeatherVO {

    private Integer temperature;

    private Integer weatherCode;

    private Integer minTemperature;

    private Integer maxTemperature;

    /** 目标日期（yyyy-MM-dd），为空表示当前天气 */
    private String targetDate;
}
