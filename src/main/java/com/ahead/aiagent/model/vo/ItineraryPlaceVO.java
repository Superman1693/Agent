package com.ahead.aiagent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ItineraryPlaceVO implements Serializable {

    private Long id;

    private String time;

    private String name;

    private String type;

    private String imageUrl;

    private String distance;

    private String duration;

    private List<String> tags;

    private static final long serialVersionUID = 1L;
}
