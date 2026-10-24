package com.ahead.aiagent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ahead.aiagent.mapper.DestinationMapper;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.model.entity.Destination;
import com.ahead.aiagent.model.vo.DestinationVO;
import com.ahead.aiagent.service.DestinationService;
import com.ahead.aiagent.tools.WebSearchTool;
import com.ahead.aiagent.utils.LlmRetryUtils;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
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
 * 目的地服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DestinationServiceImpl implements DestinationService {

    private final DestinationMapper destinationMapper;
    private final ChatClient.Builder chatClientBuilder;
    private final WebSearchTool webSearchTool;

    private static final String[] TAG_COLORS = { "primary", "secondary", "tertiary" };
    private static final int TOP_N = 3;

    /** 安全修复 E6/D1：Pexels Key 从环境变量注入，禁止硬编码 */
    @Value("${pexels.api-key:}")
    private String pexelsApiKey;

    /** 修复 O4：结构化抽取使用轻量模型（默认与主模型一致，行为不变） */
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

    /** 修复 O9：并行检索复用的专用线程池（复用 Agent 线程池，避免额外创建） */
    @Resource(name = "agentTaskExecutor")
    private Executor refreshExecutor;

    @Override
    public List<DestinationVO> getFeaturedDestinations(Integer limit) {
        int actualLimit = (limit == null || limit < 1) ? TOP_N : Math.min(limit, 50);

        // 查询启用的目的地，按分数降序
        LambdaQueryWrapper<Destination> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Destination::getIsActive, 1)
                .orderByDesc(Destination::getScore)
                .last("LIMIT " + actualLimit);

        List<Destination> destinations = destinationMapper.selectList(wrapper);

        return destinations.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public DestinationVO getDestinationDetail(Long id) {
        Destination destination = destinationMapper.selectById(id);
        if (destination == null || !Objects.equals(destination.getIsActive(), 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "目的地不存在");
        }
        return convertToVO(destination);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshDestinations() {
        log.info("开始刷新精选目的地数据...");

        try {
            // 1. 使用 AI + 网页搜索生成候选数据
            List<Destination> candidates = generateCandidates();

            if (candidates.isEmpty()) {
                log.warn("未生成任何候选目的地");
                return;
            }

            // 2. 去重和清洗
            List<Destination> cleaned = cleanAndDeduplicate(candidates);

            // 3. 打分排序，选择 Top 3
            List<Destination> top3 = selectTop3(cleaned);

            // 4. Upsert 入库
            upsertDestinations(top3);

            log.info("精选目的地刷新完成，共更新 {} 条数据", top3.size());

        } catch (Exception e) {
            log.error("刷新精选目的地失败", e);
            throw e;
        }
    }

    /**
     * 使用 AI + 网页搜索生成候选目的地
     */
    private List<Destination> generateCandidates() {
        // 定义多个搜索关键词，增加多样性
        String[] keywords = {
                "2026年热门旅游目的地推荐",
                "小众旅游景点推荐",
                "自然风光旅游胜地"
        };

        // 修复 O9：原实现串行处理 3 个关键词并 Thread.sleep(1000) 限速，总耗时 ≈ 3×(搜索+LLM)+3s。
        // 改为并行执行，把串行等待压缩为一个并发窗口；可用 app.refresh.parallel-enabled=false 回退串行。
        List<CompletableFuture<List<Destination>>> futures = new ArrayList<>(keywords.length);
        for (String keyword : keywords) {
            if (refreshParallelEnabled) {
                futures.add(CompletableFuture.supplyAsync(() -> extractByKeyword(keyword), refreshExecutor));
            } else {
                futures.add(CompletableFuture.completedFuture(extractByKeyword(keyword)));
            }
        }

        List<Destination> candidates = new ArrayList<>();
        for (CompletableFuture<List<Destination>> future : futures) {
            try {
                candidates.addAll(future.join());
            } catch (Exception e) {
                log.error("关键词候选抽取失败", e);
            }
        }

        return candidates;
    }

    /** 单个关键词的「网页搜索 + AI 抽取」流程（供并行调用，内部自吞异常避免拖垮整批） */
    private List<Destination> extractByKeyword(String keyword) {
        try {
            String searchResult = webSearchTool.search(keyword);
            return extractDestinationsFromSearch(searchResult, keyword);
        } catch (Exception e) {
            log.error("搜索关键词 {} 失败", keyword, e);
            return Collections.emptyList();
        }
    }

    /**
     * 使用 AI 从搜索结果中提取目的地信息
     */
    private List<Destination> extractDestinationsFromSearch(String searchResult, String keyword) {
        String prompt = String.format("""
                请从以下搜索结果中提取旅游目的地信息，返回 JSON 数组格式。

                要求：
                1. 提取 3-5 个不同的旅游目的地
                2. 每个目的地必须包含：name（目的地名称）、country（国家/地区）、description（简短描述，50字以内）、tag（标签，如：高热度/自然/文化/美食）
                3. description 要吸引人，突出特色
                4. 只返回 JSON 数组，不要其他内容

                JSON 格式示例：
                [
                  {
                    "name": "东京",
                    "country": "日本",
                    "description": "霓虹街头的都市浪漫",
                    "tag": "高热度"
                  }
                ]

                搜索结果：
                %s
                """, searchResult);

        try {
            // 修复 O4：抽取步骤使用轻量模型（app.llm.extract-model，默认与主模型一致，行为不变）
            ChatOptions extractOptions = DashScopeChatOptions.builder().withModel(extractModel).build();
            ChatClient chatClient = chatClientBuilder.build();
            // 修复 O2：对限流（429）与瞬时 5xx / 连接抖动做指数退避重试
            String response = LlmRetryUtils.callWithRetry(
                    () -> chatClient.prompt(new Prompt(List.of(new UserMessage(prompt)), extractOptions))
                            .call()
                            .content(),
                    llmRetryMaxAttempts,
                    llmRetryInitialBackoffMs,
                    llmRetryMaxBackoffMs,
                    "destination.extract");

            // 解析 JSON
            return parseDestinationsFromJson(response, keyword);

        } catch (Exception e) {
            log.error("AI 提取目的地信息失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析 JSON 响应为 Destination 列表
     */
    private List<Destination> parseDestinationsFromJson(String jsonResponse, String keyword) {
        List<Destination> destinations = new ArrayList<>();

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

                Destination dest = new Destination();
                dest.setName(obj.getStr("name"));
                dest.setCountry(obj.getStr("country"));
                dest.setDescription(obj.getStr("description"));
                dest.setTag(obj.getStr("tag"));
                dest.setSourceKeyword(keyword);
                dest.setIsActive(1);

                // 分配标签颜色
                dest.setTagColor(TAG_COLORS[i % TAG_COLORS.length]);

                // 生成真实图片 URL
                dest.setImageUrl(searchDestinationImage(dest.getName(), dest.getCountry()));

                // 初始分数（修复 E35/D25：改用名称+国家的稳定哈希，保证同一候选两次运行结果可复现）
                double stableScore = 50.0 + (Math.floorMod(Objects.hash(dest.getName(), dest.getCountry()), 5000) / 100.0);
                dest.setScore(BigDecimal.valueOf(stableScore));

                destinations.add(dest);
            }

        } catch (Exception e) {
            log.error("解析 JSON 失败: {}", jsonResponse, e);
        }

        return destinations;
    }

    /**
     * 使用 Pexels API 搜索真实图片
     */
    private String searchDestinationImage(String name, String country) {
        // 修复 E6/D1：未配置 Key 时直接返回占位图，避免使用硬编码密钥
        if (StrUtil.isBlank(pexelsApiKey)) {
            return fallbackImage(name);
        }
        try {
            String query = name + " " + country + " travel";

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", pexelsApiKey);

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
            log.error("搜索图片失败: {}", name, e);
        }

        // 失败时返回稳定占位图
        return fallbackImage(name);
    }

    private String fallbackImage(String name) {
        int seed = Math.abs(Objects.hashCode(name));
        return String.format("https://picsum.photos/seed/%d/800/600", seed);
    }

    /**
     * 去重和数据清洗
     */
    private List<Destination> cleanAndDeduplicate(List<Destination> candidates) {
        Map<String, Destination> uniqueMap = new LinkedHashMap<>();

        for (Destination dest : candidates) {
            // 数据验证
            if (StrUtil.isBlank(dest.getName()) ||
                    StrUtil.isBlank(dest.getCountry()) ||
                    StrUtil.isBlank(dest.getDescription()) ||
                    StrUtil.isBlank(dest.getTag())) {
                continue;
            }

            // 去重：name + country 作为唯一键
            String key = dest.getName() + "|" + dest.getCountry();

            // 如果已存在，保留分数更高的
            if (uniqueMap.containsKey(key)) {
                Destination existing = uniqueMap.get(key);
                if (dest.getScore().compareTo(existing.getScore()) > 0) {
                    uniqueMap.put(key, dest);
                }
            } else {
                uniqueMap.put(key, dest);
            }
        }

        return new ArrayList<>(uniqueMap.values());
    }

    /**
     * 选择 Top 3
     */
    private List<Destination> selectTop3(List<Destination> destinations) {
        return destinations.stream()
                .sorted(Comparator.comparing(Destination::getScore).reversed())
                .limit(TOP_N)
                .collect(Collectors.toList());
    }

    /**
     * Upsert 入库（幂等操作）
     */
    private void upsertDestinations(List<Destination> destinations) {
        LocalDateTime now = LocalDateTime.now();

        for (Destination dest : destinations) {
            // 查询是否已存在
            LambdaQueryWrapper<Destination> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Destination::getName, dest.getName())
                    .eq(Destination::getCountry, dest.getCountry());

            Destination existing = destinationMapper.selectOne(wrapper);

            if (existing != null) {
                // 更新
                dest.setId(existing.getId());
                dest.setCreateTime(existing.getCreateTime());
                dest.setUpdateTime(now);
                destinationMapper.updateById(dest);
            } else {
                // 插入（修复 E30/D20：并发下唯一键冲突降级为更新，避免异常中断整个 @Transactional 循环）
                dest.setCreateTime(now);
                dest.setUpdateTime(now);
                try {
                    destinationMapper.insert(dest);
                } catch (DuplicateKeyException dup) {
                    log.warn("目的地并发插入冲突，降级为更新: {}|{}", dest.getName(), dest.getCountry());
                    Destination concurrent = destinationMapper.selectOne(new LambdaQueryWrapper<Destination>()
                            .eq(Destination::getName, dest.getName())
                            .eq(Destination::getCountry, dest.getCountry()));
                    if (concurrent != null) {
                        dest.setId(concurrent.getId());
                        dest.setCreateTime(concurrent.getCreateTime());
                        destinationMapper.updateById(dest);
                    }
                }
            }
        }

        // 将其他记录设为非活跃
        LambdaUpdateWrapper<Destination> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Destination::getIsActive, 0)
                .notIn(Destination::getId,
                        destinations.stream()
                                .map(Destination::getId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList()));

        if (!destinations.isEmpty() && destinations.stream().anyMatch(d -> d.getId() != null)) {
            destinationMapper.update(null, updateWrapper);
        }
    }

    private DestinationVO convertToVO(Destination destination) {
        DestinationVO vo = BeanUtil.copyProperties(destination, DestinationVO.class);

        // 前端详情页需要 tags / highlights，这里做兼容兜底
        List<String> tags = new ArrayList<>();
        if (StrUtil.isNotBlank(destination.getTag())) {
            tags.add(destination.getTag());
        }
        if (StrUtil.isNotBlank(destination.getCountry())) {
            tags.add(destination.getCountry());
        }
        vo.setTags(tags);
        // 修复 O24：标注数据来源。当前目的地数据均由大模型从搜索结果生成，价格/评分/评价数为估算值。
        vo.setDataSource("llm");

        DestinationVO.HighlightVO highlight1 = new DestinationVO.HighlightVO();
        highlight1.setIcon("landscape");
        highlight1.setTitle("目的地亮点");
        highlight1.setDescription(StrUtil.blankToDefault(destination.getDescription(), "探索当地风景与文化体验"));

        DestinationVO.HighlightVO highlight2 = new DestinationVO.HighlightVO();
        highlight2.setIcon("schedule");
        highlight2.setTitle("建议时长");
        highlight2.setDescription(StrUtil.blankToDefault(destination.getDuration(), "建议灵活安排行程"));

        DestinationVO.HighlightVO highlight3 = new DestinationVO.HighlightVO();
        highlight3.setIcon("payments");
        highlight3.setTitle("参考预算");
        String budgetText = destination.getPrice() == null
                ? "预算待确认"
                : "人均约 ¥" + destination.getPrice().toPlainString();
        highlight3.setDescription(budgetText);

        vo.setHighlights(List.of(highlight1, highlight2, highlight3));

        return vo;
    }
}
