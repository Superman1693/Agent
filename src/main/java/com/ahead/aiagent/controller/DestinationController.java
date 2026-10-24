package com.ahead.aiagent.controller;

import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ResultUtils;
import com.ahead.aiagent.model.vo.DestinationVO;
import com.ahead.aiagent.service.DestinationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public BaseResponse<List<DestinationVO>> getFeaturedDestinations(
            @RequestParam(required = false) Integer limit) {
        List<DestinationVO> destinations = destinationService.getFeaturedDestinations(limit);
        return ResultUtils.success(destinations);
    }

    @Operation(summary = "获取目的地详情")
    @GetMapping("/{id}")
    public BaseResponse<DestinationVO> getDestinationDetail(@PathVariable Long id) {
        DestinationVO destination = destinationService.getDestinationDetail(id);
        return ResultUtils.success(destination);
    }

    @Operation(summary = "手动刷新精选目的地（需登录）")
    // 修复 E4/D2：由 GET 改为 POST，并纳入鉴权拦截，避免匿名/预取器反复触发昂贵任务
    @PostMapping("/refresh")
    public BaseResponse<String> refreshDestinations() {
        destinationService.refreshDestinations();
        return ResultUtils.success("刷新成功");
    }
}
