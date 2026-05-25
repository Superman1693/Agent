package com.ahead.aiagent.controller;

import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.common.ResultUtils;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.model.dto.*;
import com.ahead.aiagent.model.vo.*;
import com.ahead.aiagent.security.AuthContext;
import com.ahead.aiagent.service.ItineraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 行程管理控制器
 */
@Tag(name = "行程管理")
@RestController
@RequestMapping("/itinerary")
@RequiredArgsConstructor
public class ItineraryController {

    private final ItineraryService itineraryService;

    @Operation(summary = "创建行程")
    @PostMapping("/create")
    public BaseResponse<ItineraryVO> createItinerary(@Valid @RequestBody ItineraryCreateRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        ItineraryVO itinerary = itineraryService.createItinerary(request, userId);
        return ResultUtils.success(itinerary, "ok");
    }

    @Operation(summary = "根据 AI 结果创建行程")
    @PostMapping("/create-from-ai")
    public BaseResponse<ItineraryVO> createFromAi(@Valid @RequestBody AiItineraryCreateRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        ItineraryVO itinerary = itineraryService.createItineraryFromAi(request, userId);
        return ResultUtils.success(itinerary, "ok");
    }

    @Operation(summary = "获取行程列表")
    @GetMapping("/list")
    public BaseResponse<List<ItineraryVO>> listItineraries(
            @RequestParam(required = false) String status) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        List<ItineraryVO> itineraries = itineraryService.listItineraries(status, userId);
        return ResultUtils.success(itineraries, "ok");
    }

    @Operation(summary = "获取行程详情")
    @GetMapping("/{id}")
    public BaseResponse<ItineraryDetailVO> getItineraryDetail(@PathVariable Long id) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        ItineraryDetailVO detail = itineraryService.getItineraryDetail(id, userId);
        return ResultUtils.success(detail, "ok");
    }

    @Operation(summary = "更新行程基础信息")
    @PutMapping("/{id}")
    public BaseResponse<ItineraryVO> updateItinerary(
            @PathVariable Long id,
            @Valid @RequestBody ItineraryUpdateRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        ItineraryVO itinerary = itineraryService.updateItinerary(id, request, userId);
        return ResultUtils.success(itinerary, "ok");
    }

    @Operation(summary = "删除行程")
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> deleteItinerary(@PathVariable Long id) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        boolean result = itineraryService.deleteItinerary(id, userId);
        return ResultUtils.success(result, "ok");
    }

    @Operation(summary = "新增行程项")
    @PostMapping("/{itineraryId}/place")
    public BaseResponse<ItineraryPlaceVO> addPlace(
            @PathVariable Long itineraryId,
            @Valid @RequestBody ItineraryPlaceAddRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        ItineraryPlaceVO place = itineraryService.addPlace(itineraryId, request, userId);
        return ResultUtils.success(place, "ok");
    }

    @Operation(summary = "更新行程项")
    @PutMapping("/place/{placeId}")
    public BaseResponse<ItineraryPlaceVO> updatePlace(
            @PathVariable Long placeId,
            @Valid @RequestBody ItineraryPlaceUpdateRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        ItineraryPlaceVO place = itineraryService.updatePlace(placeId, request, userId);
        return ResultUtils.success(place, "ok");
    }

    @Operation(summary = "删除行程项")
    @DeleteMapping("/place/{placeId}")
    public BaseResponse<Boolean> deletePlace(@PathVariable Long placeId) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        boolean result = itineraryService.deletePlace(placeId, userId);
        return ResultUtils.success(result, "ok");
    }
}
