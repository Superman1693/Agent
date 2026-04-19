package com.ahead.aiagent.controller;

import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ResultUtils;
import com.ahead.aiagent.model.vo.DestinationVO;
import com.ahead.aiagent.service.DestinationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 目的地控制器
 */
@Tag(name = "目的地管理")
@RestController
@RequestMapping("/destination")
@RequiredArgsConstructor
public class DestinationController {

    private final DestinationService destinationService;

    @Operation(summary = "获取本周精选目的地")
    @GetMapping("/featured")
    public BaseResponse<List<DestinationVO>> getFeaturedDestinations() {
        List<DestinationVO> destinations = destinationService.getFeaturedDestinations();
        return ResultUtils.success(destinations);
    }

    @Operation(summary = "手动刷新精选目的地（测试用）")
    @GetMapping("/refresh")
    public BaseResponse<String> refreshDestinations() {
        destinationService.refreshDestinations();
        return ResultUtils.success("刷新成功");
    }
}
