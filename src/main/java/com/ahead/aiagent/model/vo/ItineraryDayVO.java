package com.ahead.aiagent.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

@Data
public class ItineraryDayVO implements Serializable {

    private Integer dayNumber;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private List<ItineraryPlaceVO> places;

    private static final long serialVersionUID = 1L;
}
