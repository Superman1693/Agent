package com.ahead.aiagent.service;

import com.ahead.aiagent.model.dto.*;
import com.ahead.aiagent.model.vo.*;

import java.util.List;

public interface ItineraryService {

    /**
     * 创建行程
     */
    ItineraryVO createItinerary(ItineraryCreateRequest request, Long userId);

    /**
     * 根据 AI 结果创建行程
     */
    ItineraryVO createItineraryFromAi(AiItineraryCreateRequest request, Long userId);

    /**
     * 获取行程列表
     */
    List<ItineraryVO> listItineraries(String status, Long userId);

    /**
     * 获取行程详情
     */
    ItineraryDetailVO getItineraryDetail(Long id, Long userId);

    /**
     * 更新行程基础信息
     */
    ItineraryVO updateItinerary(Long id, ItineraryUpdateRequest request, Long userId);

    /**
     * 删除行程
     */
    boolean deleteItinerary(Long id, Long userId);

    /**
     * 新增行程项
     */
    ItineraryPlaceVO addPlace(Long itineraryId, ItineraryPlaceAddRequest request, Long userId);

    /**
     * 更新行程项
     */
    ItineraryPlaceVO updatePlace(Long placeId, ItineraryPlaceUpdateRequest request, Long userId);

    /**
     * 删除行程项
     */
    boolean deletePlace(Long placeId, Long userId);

    /**
     * 功能4：批量调整行程地点排序（同一天内按 sortOrder 升序展示）
     */
    void reorderPlaces(ItineraryPlaceReorderRequest request, Long userId);

    /**
     * 功能5：生成（或幂等获取）行程分享令牌
     */
    ItineraryShareVO shareItinerary(Long id, Long userId);

    /**
     * 功能5：免登录按分享令牌获取行程详情（token 无效抛 NOT_FOUND_ERROR）
     */
    ItineraryDetailVO getItineraryDetailByShareToken(String token);
}
