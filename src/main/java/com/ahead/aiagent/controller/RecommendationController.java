package com.ahead.aiagent.controller;

import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.common.ResultUtils;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.model.dto.RecommendationQueryRequest;
import com.ahead.aiagent.model.vo.RecommendationPageVO;
import com.ahead.aiagent.model.vo.RecommendationVO;
import com.ahead.aiagent.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 热门推荐控制器
 */
@Slf4j
@RestController
@RequestMapping("/recommendation")
@RequiredArgsConstructor
@Tag(name = "热门推荐", description = "热门推荐相关接口")
public class RecommendationController {

    private final RecommendationService recommendationService;

    // 修复 E5/D3：由 GET 改为 POST 并纳入鉴权拦截；同时移除写库副作用（见 Service 层），
    // 避免匿名用户用随机 location 批量注入 LLM 生成的垃圾数据污染推荐表。
    @PostMapping("/hotdata")
    @Operation(summary = "根据用户位置获取附近热门景点（Top 3）")
    public BaseResponse<RecommendationPageVO> getNearbyRecommendations(@RequestParam String location) {
        try {
            if (cn.hutool.core.util.StrUtil.isBlank(location)) {
                return ResultUtils.error(ErrorCode.PARAMS_ERROR, "位置参数不能为空", "");
            }

            RecommendationPageVO result = recommendationService.getNearbyRecommendations(location);
            return ResultUtils.success(result);

        } catch (Exception e) {
            log.error("获取附近热门景点失败", e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    @GetMapping("/hot")
    @Operation(summary = "获取热门推荐列表（分页）")
    public BaseResponse<RecommendationPageVO> getHotRecommendations(RecommendationQueryRequest request) {
        try {
            if (request == null) {
                request = new RecommendationQueryRequest();
            }

            RecommendationPageVO result = recommendationService.getHotRecommendations(request);
            return ResultUtils.success(result);

        } catch (BusinessException e) {
            log.error("获取热门推荐失败: {}", e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage(), "");
        } catch (Exception e) {
            log.error("获取热门推荐失败", e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    // 修复 E4/D2：由 GET 改为 POST，并纳入鉴权拦截，避免匿名反复触发昂贵任务
    @PostMapping("/refresh")
    @Operation(summary = "手动刷新热门推荐数据（需登录）")
    public BaseResponse<String> refreshRecommendations() {
        try {
            log.info("手动触发热门推荐刷新");
            recommendationService.refreshRecommendations();
            return ResultUtils.success("刷新成功");

        } catch (Exception e) {
            log.error("刷新热门推荐失败", e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    // 修复 E23：补齐前端详情页所需的单条查询接口（写法参照 DestinationController GET /{id}）
    @GetMapping("/{id}")
    @Operation(summary = "获取推荐单条详情（含 dataSource 字段）")
    public BaseResponse<RecommendationVO> getRecommendationDetail(@PathVariable Long id) {
        return ResultUtils.success(recommendationService.getRecommendationDetail(id));
    }
}
