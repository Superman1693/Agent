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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
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
        List<Destination> candidates = new ArrayList<>();

        // 定义多个搜索关键词，增加多样性
        String[] keywords = {
                "2026年热门旅游目的地推荐",
                "小众旅游景点推荐",
                "自然风光旅游胜地"
        };

        for (String keyword : keywords) {
            try {
                // 使用网页搜索工具
                String searchResult = webSearchTool.search(keyword);

                // 使用 AI 提取和结构化信息
                List<Destination> extracted = extractDestinationsFromSearch(searchResult, keyword);
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
            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();

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

                // 初始分数
                dest.setScore(BigDecimal.valueOf(50.0 + Math.random() * 50));

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
        try {
            String query = name + " " + country + " travel";

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
            log.error("搜索图片失败: {}", name, e);
        }

        // 失败时返回 Unsplash 占位图
        int seed = Math.abs(name.hashCode());
        return String.format("https://source.unsplash.com/800x600/?%s&sig=%d",
                name.toLowerCase().replace(" ", ","), seed);
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
                // 插入
                dest.setCreateTime(now);
                dest.setUpdateTime(now);
                destinationMapper.insert(dest);
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
