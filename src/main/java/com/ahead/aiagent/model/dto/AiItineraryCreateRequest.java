package com.ahead.aiagent.model.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

@Data
public class AiItineraryCreateRequest implements Serializable {

    /**
     * AI 最终回复文本，用于自动提取行程字段。
     */
    private String aiContent;

    /**
     * AI 生成 PDF 的下载地址，当前仅用于追踪来源。
     */
    private String pdfUrl;

    /**
     * 允许前端覆盖 AI 提取结果。
     */
    private String title;

    private String destination;

    private LocalDate startDate;

    private LocalDate endDate;

    @Min(value = 1, message = "旅行人数至少为1")
    private Integer travelers;

    /**
     * 每日行程地点（由前端结构化后传入）。
     */
    private List<AiDayPlanRequest> dayPlans;

    /**
     * 热门推荐（由前端结构化后传入）。
     */
    private List<AiRecommendationSaveRequest> recommendations;

    @Data
    public static class AiDayPlanRequest implements Serializable {

        @Min(value = 1, message = "天数至少为1")
        private Integer dayNumber;

        private String time;

        private String name;

        private String type;

        private String imageUrl;

        private String distance;

        private String duration;

        private List<String> tags;

        private static final long serialVersionUID = 1L;
    }

    @Data
    public static class AiRecommendationSaveRequest implements Serializable {

        private String title;

        private String description;

        private String imageUrl;

        private Integer price;

        private Double rating;

        private Integer reviewCount;

        private List<String> tags;

        private String location;

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
