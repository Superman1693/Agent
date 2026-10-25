package com.ahead.aiagent.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 功能4：行程地点排序请求
 */
@Data
public class ItineraryPlaceReorderRequest implements Serializable {

    @NotNull(message = "行程ID不能为空")
    private Long itineraryId;

    @NotEmpty(message = "排序列表不能为空")
    @Valid
    private List<PlaceSortItem> items;

    @Data
    public static class PlaceSortItem implements Serializable {

        @NotNull(message = "地点ID不能为空")
        private Long placeId;

        @NotNull(message = "排序值不能为空")
        private Integer sortOrder;

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
