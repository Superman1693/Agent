package com.ahead.aiagent.controller;

import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.common.ResultUtils;
import com.ahead.aiagent.mapper.DestinationMapper;
import com.ahead.aiagent.mapper.RecommendationMapper;
import com.ahead.aiagent.model.entity.Destination;
import com.ahead.aiagent.model.entity.Recommendation;
import com.ahead.aiagent.model.vo.SearchListVO;
import com.ahead.aiagent.model.vo.SearchResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 综合搜索控制器（修复 E23：前端调用的 /search 此前在后端缺失）
 */
@Tag(name = "综合搜索")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private static final int MAX_SCAN = 50;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;

    private final DestinationMapper destinationMapper;
    private final RecommendationMapper recommendationMapper;

    @Operation(summary = "综合搜索（目的地 + 推荐）")
    @GetMapping
    public BaseResponse<SearchListVO> search(
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "all") String type,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        if (!StringUtils.hasText(keyword)) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "keyword 不能为空", "");
        }
        int safePage = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int safeSize = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        String kw = keyword.trim();
        boolean includeDestination = "all".equals(type) || "destination".equals(type);
        boolean includeRecommendation = "all".equals(type) || "recommendation".equals(type);

        List<SearchResultVO> all = new ArrayList<>();

        if (includeDestination) {
            List<Destination> destinations = destinationMapper.selectList(new LambdaQueryWrapper<Destination>()
                    .eq(Destination::getIsActive, 1)
                    .and(w -> w.like(Destination::getName, kw)
                            .or().like(Destination::getCountry, kw)
                            .or().like(Destination::getDescription, kw))
                    .last("LIMIT " + MAX_SCAN));
            for (Destination d : destinations) {
                SearchResultVO vo = new SearchResultVO();
                vo.setId(d.getId());
                vo.setType("destination");
                vo.setName(d.getName());
                vo.setDescription(d.getDescription());
                vo.setImageUrl(d.getImageUrl());
                vo.setPrice(d.getPrice());
                all.add(vo);
            }
        }

        if (includeRecommendation) {
            List<Recommendation> recommendations = recommendationMapper.selectList(new LambdaQueryWrapper<Recommendation>()
                    .eq(Recommendation::getIsActive, 1)
                    .and(w -> w.like(Recommendation::getTitle, kw)
                            .or().like(Recommendation::getDescription, kw)
                            .or().like(Recommendation::getLocation, kw))
                    .last("LIMIT " + MAX_SCAN));
            for (Recommendation r : recommendations) {
                SearchResultVO vo = new SearchResultVO();
                vo.setId(r.getId());
                vo.setType("recommendation");
                vo.setName(r.getTitle());
                vo.setDescription(r.getDescription());
                vo.setImageUrl(r.getImageUrl());
                vo.setPrice(r.getPrice());
                all.add(vo);
            }
        }

        long total = all.size();
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return ResultUtils.success(new SearchListVO(new ArrayList<>(all.subList(from, to)), total));
    }
}
