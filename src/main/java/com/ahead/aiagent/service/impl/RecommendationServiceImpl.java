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
import com.ahead.aiagent.utils.LlmRetryUtils;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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

    /** 安全修复 E6/D1：Pexels Key 从环境变量注入，禁止硬编码 */
    @Value("${pexels.api-key:}")
    private String pexelsApiKey;

    /** 修复 O4：结构化抽取/生成使用轻量模型（默认与主模型一致，行为不变） */
    @Value("${app.llm.extract-model:qwen-plus}")
    private String extractModel;

    /** 修复 O2：LLM 调用重试退避参数 */
    @Value("${app.llm.retry.max-attempts:3}")
    private int llmRetryMaxAttempts;
    @Value("${app.llm.retry.initial-backoff-ms:500}")
    private long llmRetryInitialBackoffMs;
    @Value("${app.llm.retry.max-backoff-ms:4000}")
    private long llmRetryMaxBackoffMs;

    /** 修复 O9：关键词检索是否并行执行 */
    @Value("${app.refresh.parallel-enabled:true}")
    private boolean refreshParallelEnabled;

    /** 修复 O9：并行检索复用的专用线程池 */
    @Resource(name = "agentTaskExecutor")
    private Executor refreshExecutor;

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
            // 修复 E34：保留原始异常与堆栈
            log.error("查询热门推荐失败", e);
            throw new RuntimeException("查询失败，请稍后重试", e);
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
        // 定义搜索关键词
        String[] keywords = {
                "2026年热门旅游景点推荐",
                "网红打卡地推荐",
                "高性价比旅游目的地"
        };

        // 修复 O9：原实现串行处理 3 个关键词并 Thread.sleep(1000) 限速，改为并行执行；
        // 可用 app.refresh.parallel-enabled=false 回退为串行。
        List<CompletableFuture<List<Recommendation>>> futures = new ArrayList<>(keywords.length);
        for (String keyword : keywords) {
            if (refreshParallelEnabled) {
                futures.add(CompletableFuture.supplyAsync(() -> collectByKeyword(keyword), refreshExecutor));
            } else {
                futures.add(CompletableFuture.completedFuture(collectByKeyword(keyword)));
            }
        }

        List<Recommendation> candidates = new ArrayList<>();
        for (CompletableFuture<List<Recommendation>> future : futures) {
            try {
                candidates.addAll(future.join());
            } catch (Exception e) {
                log.error("关键词候选采集失败", e);
            }
        }

        return candidates;
    }

    /** 单个关键词的「网页搜索 + AI 抽取」流程（供并行调用，内部自吞异常避免拖垮整批） */
    private List<Recommendation> collectByKeyword(String keyword) {
        try {
            String searchResult = webSearchTool.search(keyword);
            return extractRecommendationsFromSearch(searchResult, keyword);
        } catch (Exception e) {
            log.error("搜索关键词 {} 失败", keyword, e);
            return Collections.emptyList();
        }
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
        Set<String> usedImageUrls = new HashSet<>();

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

                // 根据景点标题 + 地点检索图片，并在同一批次内去重
                rec.setImageUrl(generateRelevantImageUrl(rec, usedImageUrls));

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
    private String generateRelevantImageUrl(Recommendation rec, Set<String> usedImageUrls) {
        String title = StrUtil.blankToDefault(rec.getTitle(), "").trim();
        String location = StrUtil.blankToDefault(rec.getLocation(), "").trim();

        List<String> queryCandidates = new ArrayList<>();

        // 优先景点名称本身，提升图片匹配度
        if (StrUtil.isNotBlank(title)) {
            queryCandidates.add(title + " 景点");
            queryCandidates.add(title + " landmark");
        }

        // 次选标题+地点，避免仅按地点导致同城同图
        if (StrUtil.isNotBlank(title) && StrUtil.isNotBlank(location)) {
            queryCandidates.add(title + " " + location + " 旅游");
            queryCandidates.add(title + " " + location + " attraction");
        }

        // 再次选地点关键词
        if (StrUtil.isNotBlank(location)) {
            queryCandidates.add(location + " 景点");
            queryCandidates.add(location + " travel attraction");
        }

        for (String query : queryCandidates) {
            String imageUrl = searchPexelsImage(query, title + location, usedImageUrls);
            if (StrUtil.isNotBlank(imageUrl)) {
                usedImageUrls.add(imageUrl);
                return imageUrl;
            }
        }

        // 失败时返回稳定占位图
        int seed = Math.abs((location + title).hashCode());
        return String.format("https://picsum.photos/seed/%d/800/600", seed);
    }

    private String searchPexelsImage(String query, String selectionSeed, Set<String> usedImageUrls) {
        // 修复 E6/D1：未配置 Key 时跳过，直接走占位图
        if (StrUtil.isBlank(pexelsApiKey)) {
            return null;
        }
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", pexelsApiKey);

            Map<String, Object> params = new HashMap<>();
            params.put("query", query);
            params.put("per_page", "12");
            params.put("orientation", "landscape");

            String response = cn.hutool.http.HttpUtil.createGet("https://api.pexels.com/v1/search")
                    .addHeaders(headers)
                    .form(params)
                    .execute()
                    .body();

            JSONObject jsonResponse = JSONUtil.parseObj(response);
            JSONArray photos = jsonResponse.getJSONArray("photos");

            if (photos != null && !photos.isEmpty()) {
                int start = Math.abs(Objects.hash(selectionSeed, query)) % photos.size();
                for (int i = 0; i < photos.size(); i++) {
                    int idx = (start + i) % photos.size();
                    JSONObject photo = photos.getJSONObject(idx);
                    JSONObject src = photo.getJSONObject("src");
                    String imageUrl = src == null ? null : src.getStr("large");
                    if (StrUtil.isBlank(imageUrl)) {
                        continue;
                    }
                    if (usedImageUrls.contains(imageUrl)) {
                        continue;
                    }
                    return imageUrl;
                }
            }
        } catch (Exception e) {
            log.error("搜索图片失败: {}", query, e);
        }
        return null;
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
                // 插入（修复 E30/D20：并发唯一键冲突降级为更新，避免中断整个 @Transactional 循环）
                rec.setCreateTime(now);
                rec.setUpdateTime(now);
                try {
                    recommendationMapper.insert(rec);
                } catch (DuplicateKeyException dup) {
                    log.warn("推荐并发插入冲突，降级为更新: {}", rec.getTitle());
                    Recommendation concurrent = recommendationMapper.selectOne(
                            new LambdaQueryWrapper<Recommendation>().eq(Recommendation::getTitle, rec.getTitle()));
                    if (concurrent != null) {
                        rec.setId(concurrent.getId());
                        rec.setCreateTime(concurrent.getCreateTime());
                        recommendationMapper.updateById(rec);
                    }
                }
            }
        }
    }

    /**
     * 根据用户位置获取附近热门景点推荐
     */
    @Override
    @Cacheable(value = "nearbyRecommendations", key = "'v2:' + #userLocation")
    public RecommendationPageVO getNearbyRecommendations(String userLocation) {
        long startTime = System.currentTimeMillis();

        try {
            if (StrUtil.isBlank(userLocation)) {
                userLocation = "北京"; // 默认位置
            }

            log.info("查询用户位置 {} 附近的热门景点", userLocation);

            // 使用 AI 生成该位置附近的热门景点推荐
            List<Recommendation> recommendations = generateNearbyRecommendations(userLocation);

            // 修复 E5/D3：不再执行 upsertRecommendations 写库。
            // 该接口过去允许匿名传入任意 location 并落库，可被批量注入垃圾数据污染推荐表；
            // 现改为纯实时生成 + 缓存返回，写库仅发生在受鉴权保护的 refresh 流程中。
            calculateScores(recommendations);

            // 未入库，使用负数临时 id（见 ensureRecommendationIds），与真实 DB 自增 id 空间隔离
            ensureRecommendationIds(recommendations, userLocation);

            // 转换为 VO
            List<RecommendationVO> voList = recommendations.stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            long duration = System.currentTimeMillis() - startTime;
            log.info("查询附近热门景点完成，耗时: {}ms, 返回条数: {}", duration, voList.size());

            return new RecommendationPageVO(voList, (long) voList.size(), 1, voList.size());

        } catch (Exception e) {
            // 修复 E34：保留原始异常与堆栈，便于排障
            log.error("查询附近热门景点失败", e);
            throw new RuntimeException("查询失败，请稍后重试", e);
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
            // 修复 O4：结构化生成使用轻量模型（app.llm.extract-model）
            ChatOptions extractOptions = DashScopeChatOptions.builder().withModel(extractModel).build();
            ChatClient chatClient = chatClientBuilder.build();
            // 修复 O2：对限流/瞬时故障做指数退避重试
            String response = LlmRetryUtils.callWithRetry(
                    () -> chatClient.prompt(new Prompt(List.of(new UserMessage(prompt)), extractOptions))
                            .call()
                            .content(),
                    llmRetryMaxAttempts,
                    llmRetryInitialBackoffMs,
                    llmRetryMaxBackoffMs,
                    "recommendation.nearby");

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
        // 修复 O24：标注数据来源。当前推荐数据均由大模型生成，价格/评分/评价数为估算值。
        vo.setDataSource("llm");
        return vo;
    }

    private void ensureRecommendationIds(List<Recommendation> recommendations, String userLocation) {
        for (int i = 0; i < recommendations.size(); i++) {
            Recommendation rec = recommendations.get(i);
            if (rec.getId() != null) {
                continue;
            }
            // 修复 E36/D26：原用 Math.abs(Objects.hash(...)) 生成假 id，与真实 DB 自增 id 处于同一
            // 数值空间，存在碰撞风险（前端会跳转到另一个商品）。改用负数临时 id，明确与真实 id 隔离。
            rec.setId(-(long) (i + 1));
        }
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
