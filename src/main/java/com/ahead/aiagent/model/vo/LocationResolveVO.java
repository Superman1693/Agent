package com.ahead.aiagent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 经纬度逆地理编码结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationResolveVO {

    private String displayText;

    private String queryText;

    private String province;

    private String city;

    private String district;


    private String formattedAddress;

    private Double latitude;

    private Double longitude;

    private String source;
}
