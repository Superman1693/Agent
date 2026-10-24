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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 综合搜索控制器（修复 E23：前端调用的 /search 此前在后端缺失）。
 *
 * 修复 E28/D19：由「LIMIT 50 拉全量 + 内存截断分页」改为 SQL 真分页（selectPage），
 * total 使用分页结果的真实 total。
 *
 * 注意：type=all 时对两张表各取当前页后按 score 降序合并再截取每页条数，
 * 合并排序为「近似排序」（跨页的全局排序不精确），total 为两表真实 total 相加；
 * 需要精确全局排序时应使用独立的单类型搜索或搜索引擎（如 ES）。
 */
@Tag(name = "综合搜索")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

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

        String kw = escapeLike(keyword.trim());
        boolean includeDestination = "all".equals(type) || "destination".equals(type);
        boolean includeRecommendation = "all".equals(type) || "recommendation".equals(type);

        if (includeDestination && includeRecommendation) {
            // type=all：两表各查当前页，按 score 合并（近似排序，见类注释），total 相加
            Page<Destination> destPage = destinationMapper.selectPage(new Page<>(safePage, safeSize),
                    destinationWrapper(kw));
            Page<Recommendation> recPage = recommendationMapper.selectPage(new Page<>(safePage, safeSize),
                    recommendationWrapper(kw));

            record Scored(SearchResultVO vo, BigDecimal score) {
            }
            List<Scored> merged = new ArrayList<>();
            destPage.getRecords().forEach(d -> merged.add(new Scored(toDestinationVO(d), d.getScore())));
            recPage.getRecords().forEach(r -> merged.add(new Scored(toRecommendationVO(r), r.getScore())));
            merged.sort(Comparator.comparing(Scored::score,
                    Comparator.nullsLast(Comparator.reverseOrder())));

            List<SearchResultVO> list = merged.stream()
                    .limit(safeSize)
                    .map(Scored::vo)
                    .toList();
            long total = destPage.getTotal() + recPage.getTotal();
            return ResultUtils.success(new SearchListVO(new ArrayList<>(list), total));
        }

        if (includeDestination) {
            // 单类型：对该表 selectPage 真分页
            Page<Destination> destPage = destinationMapper.selectPage(new Page<>(safePage, safeSize),
                    destinationWrapper(kw));
            List<SearchResultVO> list = destPage.getRecords().stream()
                    .map(this::toDestinationVO)
                    .toList();
            return ResultUtils.success(new SearchListVO(new ArrayList<>(list), destPage.getTotal()));
        }

        if (includeRecommendation) {
            Page<Recommendation> recPage = recommendationMapper.selectPage(new Page<>(safePage, safeSize),
                    recommendationWrapper(kw));
            List<SearchResultVO> list = recPage.getRecords().stream()
                    .map(this::toRecommendationVO)
                    .toList();
            return ResultUtils.success(new SearchListVO(new ArrayList<>(list), recPage.getTotal()));
        }

        // 未知 type：维持旧行为，返回空结果
        return ResultUtils.success(new SearchListVO(new ArrayList<>(), 0));
    }

    private LambdaQueryWrapper<Destination> destinationWrapper(String kw) {
        return new LambdaQueryWrapper<Destination>()
                .eq(Destination::getIsActive, 1)
                .and(w -> w.like(Destination::getName, kw)
                        .or().like(Destination::getCountry, kw)
                        .or().like(Destination::getDescription, kw))
                .orderByDesc(Destination::getScore);
    }

    private LambdaQueryWrapper<Recommendation> recommendationWrapper(String kw) {
        return new LambdaQueryWrapper<Recommendation>()
                .eq(Recommendation::getIsActive, 1)
                .and(w -> w.like(Recommendation::getTitle, kw)
                        .or().like(Recommendation::getDescription, kw)
                        .or().like(Recommendation::getLocation, kw))
                .orderByDesc(Recommendation::getScore);
    }

    private SearchResultVO toDestinationVO(Destination d) {
        SearchResultVO vo = new SearchResultVO();
        vo.setId(d.getId());
        vo.setType("destination");
        vo.setName(d.getName());
        vo.setDescription(d.getDescription());
        vo.setImageUrl(d.getImageUrl());
        vo.setPrice(d.getPrice());
        return vo;
    }

    private SearchResultVO toRecommendationVO(Recommendation r) {
        SearchResultVO vo = new SearchResultVO();
        vo.setId(r.getId());
        vo.setType("recommendation");
        vo.setName(r.getTitle());
        vo.setDescription(r.getDescription());
        vo.setImageUrl(r.getImageUrl());
        vo.setPrice(r.getPrice());
        return vo;
    }

    /** like 关键词转义 %/_/\，避免用户输入通配符导致误匹配（MySQL 默认转义符为 \） */
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
