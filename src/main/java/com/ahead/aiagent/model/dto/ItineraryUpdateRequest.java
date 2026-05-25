package com.ahead.aiagent.model.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import java.io.Serializable;
import java.time.LocalDate;

@Data
public class ItineraryUpdateRequest implements Serializable {

    private String title;

    private LocalDate startDate;

    private LocalDate endDate;

    @Min(value = 1, message = "旅行人数至少为1")
    private Integer travelers;

    private String destination;

    private static final long serialVersionUID = 1L;
}
