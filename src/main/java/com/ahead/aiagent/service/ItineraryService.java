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
}
