package com.ahead.aiagent.tools;

import com.ahead.aiagent.model.vo.WeatherVO;
import com.ahead.aiagent.service.LocationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 天气查询工具（供 Agent 调用）：内部走 LocationService 代理 open-meteo，
 * 失败时返回错误提示文案而不是抛异常，避免打断 Agent 执行链路。
 */
@Slf4j
public class WeatherTool {

    private final LocationService locationService;

    public WeatherTool(LocationService locationService) {
        this.locationService = locationService;
    }

    @Tool(name = "getWeather", description = "查询中国城市当前天气，传入城市中文名，返回温度、天气状况与出行建议")
    public String getWeather(@ToolParam(description = "城市中文名，如：北京") String city) {
        try {
            WeatherVO weather = locationService.getWeather(city, "");
            if (weather == null || weather.getTemperature() == null) {
                return "未查询到城市「" + city + "」的天气信息，请确认城市名称是否正确";
            }
            String condition = describeWeatherCode(weather.getWeatherCode());
            StringBuilder sb = new StringBuilder();
            sb.append(city).append("当前天气：").append(condition)
                    .append("，气温 ").append(weather.getTemperature()).append("℃");
            if (weather.getMinTemperature() != null && weather.getMaxTemperature() != null) {
                sb.append("（今日 ").append(weather.getMinTemperature()).append("℃ ~ ")
                        .append(weather.getMaxTemperature()).append("℃）");
            }
            sb.append("。").append(buildAdvice(weather.getWeatherCode()));
            return sb.toString();
        } catch (Exception e) {
            // 任何异常都只返回提示文案，不向上抛出，避免打断 Agent 的工具调用链
            log.warn("WeatherTool 查询天气失败: city={}, {}", city, e.getMessage());
            return "天气查询失败，请稍后再试";
        }
    }

    /** WMO 天气代码 → 中文描述（open-meteo 使用 WMO 标准编码） */
    private String describeWeatherCode(Integer code) {
        if (code == null) {
            return "未知";
        }
        return switch (code) {
            case 0 -> "晴";
            case 1, 2 -> "多云";
            case 3 -> "阴";
            case 45, 48 -> "雾";
            case 51, 53, 55, 56, 57 -> "毛毛雨";
            case 61, 63, 65, 66, 67, 80, 81, 82 -> "雨";
            case 71, 73, 75, 77, 85, 86 -> "雪";
            case 95, 96, 99 -> "雷阵雨";
            default -> "天气多变";
        };
    }

    /** 按天气状况给出简短出行建议 */
    private String buildAdvice(Integer code) {
        if (code == null) {
            return "出行前请再次确认天气情况。";
        }
        if (code == 0 || code == 1 || code == 2) {
            return "天气不错，适合出行游玩，注意防晒补水。";
        }
        if (code == 71 || code == 73 || code == 75 || code == 77 || code == 85 || code == 86) {
            return "有降雪，出行注意保暖防滑，建议穿防滑鞋。";
        }
        if (code == 95 || code == 96 || code == 99) {
            return "有雷雨，建议避开户外活动，出行请携带雨具并注意安全。";
        }
        if (code == 45 || code == 48) {
            return "有雾，能见度较低，驾车出行请注意安全。";
        }
        return "出行请随身携带雨具，合理安排户外行程。";
    }
}
