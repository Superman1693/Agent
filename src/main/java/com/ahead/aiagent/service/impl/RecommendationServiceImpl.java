package com.ahead.aiagent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ahead.aiagent.mapper.RecommendationMapper;
import com.ahead.aiagent.model.dto.RecommendationQueryRequest;
import com.ahead.aiagent.model.entity.Recommendation;
import com.ahead.aiagent.model.vo.RecommendationPageVO;
import com.ahead.aiagent.model.vo.RecommendationVO;
import com.ahead.aiagent.service.RecommendationService;
import com.ahead.aiagent.tools.WebSearchTool;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 热门推荐服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final RecommendationMapper recommendationMapper;
    private final ChatClient.Builder chatClientBuilder;
    private final WebSearchTool webSearchTool;

    private static final int MIN_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    @Override
    @Cacheable(value = "hotRecommendations", key = "#request.page + '_' + #request.size + '_' + #request.sortBy + '_' + #request.sortOrder")
    public RecommendationPageVO getHotRecommendations(RecommendationQueryRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 参数校验和兜底
            Integer page = validatePage(request.getPage());
            Integer size = validateSize(request.getSize());
            String sortBy = validateSortBy(request.getSortBy());
            String sortOrder = validateSortOrder(request.getSortOrder());

            // 构建查询
            LambdaQueryWrapper<Recommendation> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Recommendation::getIsActive, 1);

            // 排序
            if ("score".equals(sortBy)) {
                wrapper.orderBy(true, "desc".equals(sortOrder), Recommendation::getScore);
                wrapper.orderByDesc(Recommendation::getUpdateTime);
            } else {
                wrapper.orderBy(true, "desc".equals(sortOrder), Recommendation::getUpdateTime);
                wrapper.orderByDesc(Recommendation::getScore);
            }

            // 分页查询
            Page<Recommendation> pageResult = recommendationMapper.selectPage(
                    new Page<>(page, size), wrapper);

            // 转换为 VO
            List<RecommendationVO> voList = pageResult.getRecords().stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            long duration = System.currentTimeMillis() - startTime;
            log.info("查询热门推荐完成，耗时: {}ms, 命中条数: {}", duration, voList.size());

            return new RecommendationPageVO(voList, pageResult.getTotal(), page, size);

        } catch (Exception e) {
            log.error("查询热门推荐失败", e);
            throw new RuntimeException("查询失败，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "hotRecommendations", allEntries = true)
    public void refreshRecommendations() {
        log.info("开始刷新热门推荐数据...");

        try {
            // 1. 采集候选数据
            List<Recommendation> candidates = collectCandidates();

            if (candidates.isEmpty()) {
                log.warn("未采集到任何候选数据");
                return;
            }

            // 2. 清洗去重
            List<Recommendation> cleaned = cleanAndDeduplicate(candidates);

            // 3. 计算分数
            calculateScores(cleaned);

            // 4. 写入 MySQL
            upsertRecommendations(cleaned);

            log.info("热门推荐刷新完成，共更新 {} 条数据", cleaned.size());

        } catch (Exception e) {
            log.error("刷新热门推荐失败", e);
            throw e;
        }
    }

    /**
     * 采集候选数据
     */
    private List<Recommendation> collectCandidates() {
        List<Recommendation> candidates = new ArrayList<>();

        // 定义搜索关键词
        String[] keywords = {
                "2026年热门旅游景点推荐",
                "网红打卡地推荐",
                "高性价比旅游目的地"
        };

        for (String keyword : keywords) {
            try {
                // 使用网页搜索工具
                String searchResult = webSearchTool.search(keyword);

                // 使用 AI 提取和结构化信息
                List<Recommendation> extracted = extractRecommendationsFromSearch(searchResult, keyword);
                candidates.addAll(extracted);

                // 避免请求过快
                Thread.sleep(1000);

            } catch (Exception e) {
                log.error("搜索关键词 {} 失败", keyword, e);
            }
        }

        return candidates;
    }

    /**
     * 使用 AI 从搜索结果中提取推荐信息
     */
    private List<Recommendation> extractRecommendationsFromSearch(String searchResult, String keyword) {
        String prompt = String.format("""
                请从以下搜索结果中提取旅游推荐信息，返回 JSON 数组格式。

                要求：
                1. 提取 5-10 个不同的旅游推荐
                2. 每个推荐必须包含：
                   - title: 标题（吸引人的标题）
                   - description: 描述（50-100字）
                   - location: 位置（城市或地区）
                   - price: 价格（人民币，合理估算）
                   - rating: 评分（4.0-5.0之间）
                   - reviewCount: 评价数（100-5000之间）
                   - tags: 标签数组（2-4个标签，如：度假、高性价比、自然风光等）
                3. 只返回 JSON 数组，不要其他内容

                JSON 格式示例：
                [
                  {
                    "title": "巴厘岛奢华私人别墅：五天四晚避世指南",
                    "description": "发现乌布最隐秘的丛林秘境，享受地道水疗。",
                    "location": "巴厘岛",
                    "price": 4299,
                    "rating": 4.9,
                    "reviewCount": 1200,
                    "tags": ["度假", "高性价比"]
                  }
                ]

                搜索结果：
                %s
                """, searchResult);

        try {
            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();

            return parseRecommendationsFromJson(response, keyword);

        } catch (Exception e) {
            log.error("AI 提取推荐信息失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析 JSON 响应为 Recommendation 列表
     */
    private List<Recommendation> parseRecommendationsFromJson(String jsonResponse, String keyword) {
        List<Recommendation> recommendations = new ArrayList<>();

        try {
            // 清理可能的 markdown 代码块标记
            String cleaned = jsonResponse.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            }
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            JSONArray array = JSONUtil.parseArray(cleaned);

            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);

                Recommendation rec = new Recommendation();
                rec.setTitle(obj.getStr("title"));
                rec.setDescription(obj.getStr("description"));
                rec.setLocation(obj.getStr("location"));
                rec.setPrice(obj.getBigDecimal("price", BigDecimal.ZERO));
                rec.setRating(obj.getBigDecimal("rating", BigDecimal.valueOf(4.5)));
                rec.setReviewCount(obj.getInt("reviewCount", 100));

                // 解析标签
                JSONArray tagsArray = obj.getJSONArray("tags");
                List<String> tags = new ArrayList<>();
                if (tagsArray != null) {
                    for (int j = 0; j < tagsArray.size(); j++) {
                        tags.add(tagsArray.getStr(j));
                    }
                }
                rec.setTags(tags.isEmpty() ? List.of("推荐") : tags);

                rec.setSource(keyword);
                rec.setIsActive(1);

                // 生成稳定的图片 URL
                rec.setImageUrl(generateStableImageUrl(rec.getLocation(), rec.getTitle()));

                recommendations.add(rec);
            }

        } catch (Exception e) {
            log.error("解析 JSON 失败: {}", jsonResponse, e);
        }

        return recommendations;
    }

    /**
     * 生成稳定的图片 URL
     */
    private String generateStableImageUrl(String location, String title) {
        try {
            String query = location + " travel";

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "fVCsVgtgxpOsTnHSuHBF18xrAIz1yAQwR2uCTYCRDowxSBqL8NPweWyP");

            Map<String, Object> params = new HashMap<>();
            params.put("query", query);
            params.put("per_page", "1");

            String response = cn.hutool.http.HttpUtil.createGet("https://api.pexels.com/v1/search")
                    .addHeaders(headers)
                    .form(params)
                    .execute()
                    .body();

            JSONObject jsonResponse = JSONUtil.parseObj(response);
            JSONArray photos = jsonResponse.getJSONArray("photos");

            if (photos != null && !photos.isEmpty()) {
                JSONObject photo = photos.getJSONObject(0);
                JSONObject src = photo.getJSONObject("src");
                return src.getStr("large");
            }
        } catch (Exception e) {
            log.error("搜索图片失败: {}", location, e);
        }

        // 失败时返回稳定的占位图
        int seed = Math.abs((location + title).hashCode());
        return String.format("https://picsum.photos/seed/%d/800/600", seed);
    }

    /**
     * 清洗去重
     */
    private List<Recommendation> cleanAndDeduplicate(List<Recommendation> candidates) {
        Map<String, Recommendation> uniqueMap = new LinkedHashMap<>();

        for (Recommendation rec : candidates) {
            // 数据验证
            if (StrUtil.isBlank(rec.getTitle()) ||
                    StrUtil.isBlank(rec.getDescription()) ||
                    StrUtil.isBlank(rec.getImageUrl()) ||
                    rec.getPrice() == null || rec.getPrice().compareTo(BigDecimal.ZERO) <= 0 ||
                    rec.getRating() == null || rec.getRating().compareTo(BigDecimal.ZERO) <= 0 ||
                    rec.getReviewCount() == null || rec.getReviewCount() <= 0 ||
                    rec.getTags() == null || rec.getTags().isEmpty()) {
                continue;
            }

            // 去重：title 作为唯一键
            String key = rec.getTitle();

            if (!uniqueMap.containsKey(key)) {
                uniqueMap.put(key, rec);
            }
        }

        return new ArrayList<>(uniqueMap.values());
    }

    /**
     * 计算分数
     */
    private void calculateScores(List<Recommendation> recommendations) {
        for (Recommendation rec : recommendations) {
            // 综合评分算法：rating * 20 + log10(reviewCount) * 10
            double ratingScore = rec.getRating().doubleValue() * 20;
            double reviewScore = Math.log10(rec.getReviewCount()) * 10;
            double totalScore = ratingScore + reviewScore;

            rec.setScore(BigDecimal.valueOf(totalScore));
        }
    }

    /**
     * Upsert 入库
     */
    private void upsertRecommendations(List<Recommendation> recommendations) {
        LocalDateTime now = LocalDateTime.now();

        for (Recommendation rec : recommendations) {
            // 查询是否已存在
            LambdaQueryWrapper<Recommendation> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Recommendation::getTitle, rec.getTitle());

            Recommendation existing = recommendationMapper.selectOne(wrapper);

            if (existing != null) {
                // 更新
                rec.setId(existing.getId());
                rec.setCreateTime(existing.getCreateTime());
                rec.setUpdateTime(now);
                recommendationMapper.updateById(rec);
            } else {
                // 插入
                rec.setCreateTime(now);
                rec.setUpdateTime(now);
                recommendationMapper.insert(rec);
            }
        }
    }

    /**
     * 根据用户位置获取附近热门景点推荐
     */
    @Override
    @Cacheable(value = "nearbyRecommendations", key = "#userLocation")
    public RecommendationPageVO getNearbyRecommendations(String userLocation) {
        long startTime = System.currentTimeMillis();

        try {
            if (StrUtil.isBlank(userLocation)) {
                userLocation = "北京"; // 默认位置
            }

            log.info("查询用户位置 {} 附近的热门景点", userLocation);

            // 使用 AI 生成该位置附近的热门景点推荐
            List<Recommendation> recommendations = generateNearbyRecommendations(userLocation);

            // 转换为 VO
            List<RecommendationVO> voList = recommendations.stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            long duration = System.currentTimeMillis() - startTime;
            log.info("查询附近热门景点完成，耗时: {}ms, 返回条数: {}", duration, voList.size());

            return new RecommendationPageVO(voList, (long) voList.size(), 1, voList.size());

        } catch (Exception e) {
            log.error("查询附近热门景点失败", e);
            throw new RuntimeException("查询失败，请稍后重试");
        }
    }

    /**
     * 使用 AI 生成指定位置附近的热门景点推荐
     */
    private List<Recommendation> generateNearbyRecommendations(String userLocation) {
        String prompt = String.format("""
                请为位于"%s"的用户推荐3个附近的热门旅游景点。

                要求：
                1. 必须是距离"%s"较近的景点（同城或周边城市）
                2. 每个景点必须包含：
                   - title: 景点名称（吸引人的标题）
                   - description: 景点描述（50-100字，突出特色）
                   - location: 具体位置（城市+区域）
                   - price: 门票价格（人民币，如果免费则为0）
                   - rating: 评分（4.0-5.0之间）
                   - reviewCount: 评价数（100-5000之间）
                   - tags: 标签数组（2-4个标签，如：自然风光、历史文化、亲子游等）
                3. 只返回 JSON 数组，不要其他内容

                JSON 格式示例：
                [
                  {
                    "title": "故宫博物院：穿越紫禁城的时光之旅",
                    "description": "世界上现存规模最大、保存最完整的木质结构古建筑群，感受皇家气派。",
                    "location": "北京市东城区",
                    "price": 60,
                    "rating": 4.8,
                    "reviewCount": 15000,
                    "tags": ["历史文化", "必游景点", "亲子游"]
                  }
                ]
                """, userLocation, userLocation);

        try {
            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();

            return parseRecommendationsFromJson(response, "附近景点-" + userLocation);

        } catch (Exception e) {
            log.error("AI 生成附近景点推荐失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 转换为 VO
     */
    private RecommendationVO convertToVO(Recommendation rec) {
        RecommendationVO vo = BeanUtil.copyProperties(rec, RecommendationVO.class);
        // 确保 tags 不为 null
        if (vo.getTags() == null) {
            vo.setTags(new ArrayList<>());
        }
        return vo;
    }

    /**
     * 校验页码
     */
    private Integer validatePage(Integer page) {
        if (page == null || page < MIN_PAGE) {
            return MIN_PAGE;
        }
        return page;
    }

    /**
     * 校验每页条数
     */
    private Integer validateSize(Integer size) {
        if (size == null || size < MIN_SIZE || size > MAX_SIZE) {
            return DEFAULT_SIZE;
        }
        return size;
    }

    /**
     * 校验排序字段
     */
    private String validateSortBy(String sortBy) {
        if (StrUtil.isBlank(sortBy)) {
            return "score";
        }
        if ("updateTime".equals(sortBy) || "score".equals(sortBy)) {
            return sortBy;
        }
        return "score";
    }

    /**
     * 校验排序方向
     */
    private String validateSortOrder(String sortOrder) {
        if (StrUtil.isBlank(sortOrder)) {
            return "desc";
        }
        if ("asc".equals(sortOrder) || "desc".equals(sortOrder)) {
            return sortOrder;
        }
        return "desc";
    }
}
