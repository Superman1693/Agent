package com.ahead.aiagent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.mapper.ItineraryMapper;
import com.ahead.aiagent.mapper.ItineraryPlaceMapper;
import com.ahead.aiagent.mapper.RecommendationMapper;
import com.ahead.aiagent.model.dto.*;
import com.ahead.aiagent.model.entity.Itinerary;
import com.ahead.aiagent.model.entity.ItineraryPlace;
import com.ahead.aiagent.model.entity.Recommendation;
import com.ahead.aiagent.model.vo.*;
import com.ahead.aiagent.service.ItineraryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItineraryServiceImpl implements ItineraryService {

    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})\\s*[-./年]\\s*(\\d{1,2})\\s*[-./月]\\s*(\\d{1,2})\\s*(?:日)?");
    private static final Pattern DAYS_PATTERN = Pattern.compile("(\\d{1,2})\\s*天");
    private static final Pattern TRAVELERS_PATTERN = Pattern.compile("(\\d{1,2})\\s*(?:人|位)");
    private static final Pattern DESTINATION_HINT_PATTERN = Pattern.compile("(?:去|到|前往|目的地[是为:]?)\\s*([\\u4e00-\\u9fa5A-Za-z·]{2,20})");
    // 功能7：人均预算提取，匹配「人均约?¥?3000」/「人均 3000.50」
    private static final Pattern BUDGET_PER_PERSON_PATTERN = Pattern.compile("人均\\s*约?\\s*[¥￥]?\\s*(\\d+(?:\\.\\d+)?)");
    // 功能7：总预算兜底提取，匹配「预算[^0-9]{0,4}3000」（最多允许 4 个非数字字符，如「预算约」「预算：」）
    private static final Pattern BUDGET_TOTAL_PATTERN = Pattern.compile("预算[^0-9]{0,4}(\\d+(?:\\.\\d+)?)");

    /** 分享令牌随机源（功能5） */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ItineraryMapper itineraryMapper;
    private final ItineraryPlaceMapper itineraryPlaceMapper;
    private final RecommendationMapper recommendationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ItineraryVO createItinerary(ItineraryCreateRequest request, Long userId) {
        // 参数校验
        validateDateRange(request.getStartDate(), request.getEndDate());

        // 创建行程实体
        Itinerary itinerary = new Itinerary();
        itinerary.setUserId(userId);
        itinerary.setTitle(request.getTitle());
        itinerary.setStartDate(request.getStartDate());
        itinerary.setEndDate(request.getEndDate());
        itinerary.setTravelers(request.getTravelers());
        itinerary.setDestination(request.getDestination());
        itinerary.setBudgetPerPerson(request.getBudgetPerPerson());
        itinerary.setStatus("upcoming");

        itineraryMapper.insert(itinerary);

        return convertToVO(itinerary);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ItineraryVO createItineraryFromAi(AiItineraryCreateRequest request, Long userId) {
        String source = buildSourceText(request.getAiContent(), request.getPdfUrl());
        List<LocalDate> extractedDates = extractDates(source);

        LocalDate startDate = request.getStartDate() != null
                ? request.getStartDate()
                : (!extractedDates.isEmpty() ? extractedDates.get(0) : LocalDate.now());

        LocalDate endDate;
        if (request.getEndDate() != null) {
            endDate = request.getEndDate();
        } else if (extractedDates.size() >= 2) {
            endDate = extractedDates.get(1);
        } else {
            int days = extractDays(source);
            endDate = startDate.plusDays(Math.max(1, days) - 1L);
        }

        if (endDate.isBefore(startDate)) {
            endDate = startDate;
        }

        String destination = firstNonBlank(request.getDestination(), extractDestination(source));
        if (destination == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法识别目的地，请补充目的地后再保存");
        }

        Integer travelers = request.getTravelers();
        if (travelers == null || travelers < 1) {
            travelers = extractTravelers(source);
        }
        if (travelers == null || travelers < 1) {
            travelers = 2;
        }

        String title = firstNonBlank(request.getTitle(), extractTitle(source));
        if (title == null) {
            title = destination + "行程";
        }

        ItineraryCreateRequest createRequest = new ItineraryCreateRequest();
        createRequest.setTitle(title);
        createRequest.setDestination(destination);
        createRequest.setStartDate(startDate);
        createRequest.setEndDate(endDate);
        createRequest.setTravelers(travelers);
        // 功能7：尽力从 AI 文本提取人均预算，提取不到为 null 不阻塞保存
        createRequest.setBudgetPerPerson(extractBudgetPerPerson(source));

        ItineraryVO created = createItinerary(createRequest, userId);

        if (created.getId() != null) {
            saveAiDayPlans(created.getId(), request.getDayPlans());
            saveAiRecommendations(request.getRecommendations(), destination, userId);
        }

        return created;
    }

    @Override
    public List<ItineraryVO> listItineraries(String status, Long userId) {
        LambdaQueryWrapper<Itinerary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Itinerary::getUserId, userId);

        if (status != null && !status.isEmpty()) {
            wrapper.eq(Itinerary::getStatus, status);
        }

        wrapper.orderByDesc(Itinerary::getCreateTime);

        List<Itinerary> itineraries = itineraryMapper.selectList(wrapper);
        return itineraries.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public ItineraryDetailVO getItineraryDetail(Long id, Long userId) {
        // 查询行程
        Itinerary itinerary = itineraryMapper.selectById(id);
        if (itinerary == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "行程不存在");
        }

        // 权限校验
        if (!itinerary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该行程");
        }

        return buildDetailVO(itinerary);
    }

    /**
     * 功能5：免登录按分享令牌获取行程详情（仅分享开启的行程可见，跳过归属校验）
     */
    @Override
    public ItineraryDetailVO getItineraryDetailByShareToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "分享链接无效或已失效");
        }
        Itinerary itinerary = itineraryMapper.selectOne(new LambdaQueryWrapper<Itinerary>()
                .eq(Itinerary::getShareToken, token.trim()));
        if (itinerary == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "分享链接无效或已失效");
        }
        return buildDetailVO(itinerary);
    }

    /**
     * 组装行程详情 VO（归属详情与分享详情共用）
     */
    private ItineraryDetailVO buildDetailVO(Itinerary itinerary) {
        // 查询行程项：功能4 起排序规则为 day_number → sort_order → id
        LambdaQueryWrapper<ItineraryPlace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ItineraryPlace::getItineraryId, itinerary.getId())
                .orderByAsc(ItineraryPlace::getDayNumber)
                .orderByAsc(ItineraryPlace::getSortOrder)
                .orderByAsc(ItineraryPlace::getId);

        List<ItineraryPlace> places = itineraryPlaceMapper.selectList(wrapper);

        // 转换为VO
        return convertToDetailVO(itinerary, places);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ItineraryVO updateItinerary(Long id, ItineraryUpdateRequest request, Long userId) {
        // 查询行程
        Itinerary itinerary = itineraryMapper.selectById(id);
        if (itinerary == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "行程不存在");
        }

        // 权限校验
        if (!itinerary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该行程");
        }

        // 更新字段
        if (request.getTitle() != null) {
            itinerary.setTitle(request.getTitle());
        }
        if (request.getStartDate() != null) {
            itinerary.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            itinerary.setEndDate(request.getEndDate());
        }
        if (request.getTravelers() != null) {
            itinerary.setTravelers(request.getTravelers());
        }
        if (request.getDestination() != null) {
            itinerary.setDestination(request.getDestination());
        }
        if (request.getBudgetPerPerson() != null) {
            itinerary.setBudgetPerPerson(request.getBudgetPerPerson());
        }

        // 日期校验
        validateDateRange(itinerary.getStartDate(), itinerary.getEndDate());

        itineraryMapper.updateById(itinerary);

        return convertToVO(itinerary);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteItinerary(Long id, Long userId) {
        Itinerary itinerary = itineraryMapper.selectById(id);
        if (itinerary == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "行程不存在");
        }

        if (!itinerary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限删除该行程");
        }

        itineraryPlaceMapper.delete(new LambdaQueryWrapper<ItineraryPlace>()
                .eq(ItineraryPlace::getItineraryId, id));

        return itineraryMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ItineraryPlaceVO addPlace(Long itineraryId, ItineraryPlaceAddRequest request, Long userId) {
        // 查询行程
        Itinerary itinerary = itineraryMapper.selectById(itineraryId);
        if (itinerary == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "行程不存在");
        }

        // 权限校验
        if (!itinerary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该行程");
        }

        // 创建行程项
        ItineraryPlace place = new ItineraryPlace();
        place.setItineraryId(itineraryId);
        place.setDayNumber(request.getDayNumber());
        place.setTime(request.getTime());
        place.setName(request.getName());
        place.setType(request.getType());
        place.setImageUrl(request.getImageUrl());
        place.setDistance(request.getDistance());
        place.setDuration(request.getDuration());
        place.setTags(request.getTags());

        itineraryPlaceMapper.insert(place);

        return convertToPlaceVO(place);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ItineraryPlaceVO updatePlace(Long placeId, ItineraryPlaceUpdateRequest request, Long userId) {
        // 查询行程项
        ItineraryPlace place = itineraryPlaceMapper.selectById(placeId);
        if (place == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "行程项不存在");
        }

        // 查询行程并校验权限
        Itinerary itinerary = itineraryMapper.selectById(place.getItineraryId());
        if (itinerary == null || !itinerary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该行程项");
        }

        // 更新字段
        if (request.getDayNumber() != null) {
            place.setDayNumber(request.getDayNumber());
        }
        if (request.getTime() != null) {
            place.setTime(request.getTime());
        }
        if (request.getName() != null) {
            place.setName(request.getName());
        }
        if (request.getType() != null) {
            place.setType(request.getType());
        }
        if (request.getImageUrl() != null) {
            place.setImageUrl(request.getImageUrl());
        }
        if (request.getDistance() != null) {
            place.setDistance(request.getDistance());
        }
        if (request.getDuration() != null) {
            place.setDuration(request.getDuration());
        }
        if (request.getTags() != null) {
            place.setTags(request.getTags());
        }

        itineraryPlaceMapper.updateById(place);

        return convertToPlaceVO(place);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePlace(Long placeId, Long userId) {
        // 查询行程项
        ItineraryPlace place = itineraryPlaceMapper.selectById(placeId);
        if (place == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "行程项不存在");
        }

        // 查询行程并校验权限
        Itinerary itinerary = itineraryMapper.selectById(place.getItineraryId());
        if (itinerary == null || !itinerary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该行程项");
        }

        return itineraryPlaceMapper.deleteById(placeId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderPlaces(ItineraryPlaceReorderRequest request, Long userId) {
        // 校验行程归属
        Itinerary itinerary = itineraryMapper.selectById(request.getItineraryId());
        if (itinerary == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "行程不存在");
        }
        if (!itinerary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该行程");
        }

        // 一次性取出该行程下所有地点，避免逐条 select 造成 N+1，同时兜底校验 placeId 归属
        List<ItineraryPlace> places = itineraryPlaceMapper.selectList(new LambdaQueryWrapper<ItineraryPlace>()
                .eq(ItineraryPlace::getItineraryId, itinerary.getId())
                .select(ItineraryPlace::getId));
        Set<Long> ownedPlaceIds = places.stream()
                .map(ItineraryPlace::getId)
                .collect(Collectors.toSet());

        // 批量更新排序值（限定 itineraryId，双保险）
        for (ItineraryPlaceReorderRequest.PlaceSortItem item : request.getItems()) {
            if (!ownedPlaceIds.contains(item.getPlaceId())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "行程项不存在或不属于该行程: " + item.getPlaceId());
            }
            itineraryPlaceMapper.update(null, new LambdaUpdateWrapper<ItineraryPlace>()
                    .eq(ItineraryPlace::getId, item.getPlaceId())
                    .eq(ItineraryPlace::getItineraryId, itinerary.getId())
                    .set(ItineraryPlace::getSortOrder, item.getSortOrder()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ItineraryShareVO shareItinerary(Long id, Long userId) {
        Itinerary itinerary = itineraryMapper.selectById(id);
        if (itinerary == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "行程不存在");
        }
        if (!itinerary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限分享该行程");
        }

        // 幂等：已有分享令牌直接返回同一 token
        if (itinerary.getShareToken() == null || itinerary.getShareToken().isBlank()) {
            String token = generateShareToken();
            itinerary.setShareToken(token);
            try {
                itineraryMapper.updateById(itinerary);
            } catch (DuplicateKeyException e) {
                // 32 位随机 hex 撞唯一索引概率极低，重试一次即可
                itinerary.setShareToken(generateShareToken());
                itineraryMapper.updateById(itinerary);
            }
        }

        ItineraryShareVO vo = new ItineraryShareVO();
        vo.setShareToken(itinerary.getShareToken());
        vo.setSharePath("/share/" + itinerary.getShareToken());
        return vo;
    }

    /** 功能5：生成 32 位随机 hex 分享令牌 */
    private String generateShareToken() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * 校验日期范围
     */
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "开始日期不能晚于结束日期");
        }
    }

    /**
     * 转换为VO
     */
    private ItineraryVO convertToVO(Itinerary itinerary) {
        ItineraryVO vo = BeanUtil.copyProperties(itinerary, ItineraryVO.class);
        // 功能7：估算总预算 = 人均预算 × 人数，任一为空则不估算
        vo.setEstimatedBudget(computeEstimatedBudget(itinerary.getBudgetPerPerson(), itinerary.getTravelers()));
        return vo;
    }

    /**
     * 转换为详情VO
     */
    private ItineraryDetailVO convertToDetailVO(Itinerary itinerary, List<ItineraryPlace> places) {
        ItineraryDetailVO vo = BeanUtil.copyProperties(itinerary, ItineraryDetailVO.class);
        // 功能7：估算总预算 = 人均预算 × 人数，任一为空则不估算
        vo.setEstimatedBudget(computeEstimatedBudget(itinerary.getBudgetPerPerson(), itinerary.getTravelers()));

        // 按天分组
        Map<Integer, List<ItineraryPlace>> placesByDay = places.stream()
                .collect(Collectors.groupingBy(ItineraryPlace::getDayNumber));

        // 计算总天数
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(
                itinerary.getStartDate(), itinerary.getEndDate()) + 1;

        // 构建每天的数据
        List<ItineraryDayVO> days = new ArrayList<>();
        for (int i = 1; i <= totalDays; i++) {
            ItineraryDayVO dayVO = new ItineraryDayVO();
            dayVO.setDayNumber(i);
            dayVO.setDate(itinerary.getStartDate().plusDays(i - 1));

            List<ItineraryPlace> dayPlaces = placesByDay.getOrDefault(i, Collections.emptyList());
            dayVO.setPlaces(dayPlaces.stream()
                    .map(this::convertToPlaceVO)
                    .collect(Collectors.toList()));

            days.add(dayVO);
        }

        vo.setDays(days);
        return vo;
    }

    /**
     * 转换为PlaceVO
     */
    private ItineraryPlaceVO convertToPlaceVO(ItineraryPlace place) {
        return BeanUtil.copyProperties(place, ItineraryPlaceVO.class);
    }

    private String buildSourceText(String aiContent, String pdfUrl) {
        StringBuilder sb = new StringBuilder();
        if (aiContent != null) {
            sb.append(aiContent).append('\n');
        }
        if (pdfUrl != null) {
            sb.append(pdfUrl);
        }
        return sb.toString();
    }

    private List<LocalDate> extractDates(String text) {
        List<LocalDate> dates = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return dates;
        }

        Matcher matcher = DATE_PATTERN.matcher(text);
        while (matcher.find() && dates.size() < 2) {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));

            try {
                dates.add(LocalDate.of(year, month, day));
            } catch (Exception ignored) {
                // 忽略非法日期，继续提取后续内容
            }
        }
        return dates;
    }

    private int extractDays(String text) {
        if (text == null || text.isBlank()) {
            return 2;
        }
        Matcher matcher = DAYS_PATTERN.matcher(text);
        if (!matcher.find()) {
            return 2;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private Integer extractTravelers(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = TRAVELERS_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    /**
     * 功能7：从 AI 文本尽力提取人均预算。
     * 优先「人均约?¥?数字」，其次「预算[^0-9]{0,4}数字」；单位「万」×10000；
     * 提取不到返回 null，不阻塞行程保存。
     */
    private BigDecimal extractBudgetPerPerson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = BUDGET_PER_PERSON_PATTERN.matcher(text);
        if (!matcher.find()) {
            matcher = BUDGET_TOTAL_PATTERN.matcher(text);
            if (!matcher.find()) {
                return null;
            }
        }
        try {
            BigDecimal amount = new BigDecimal(matcher.group(1));
            // 数字后紧跟「万」则换算为元
            int end = matcher.end();
            if (end < text.length() && text.charAt(end) == '万') {
                amount = amount.multiply(BigDecimal.valueOf(10000));
            }
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return amount.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 功能7：估算总预算 = 人均预算 × 人数，任一为空返回 null */
    private BigDecimal computeEstimatedBudget(BigDecimal budgetPerPerson, Integer travelers) {
        if (budgetPerPerson == null || travelers == null || travelers <= 0) {
            return null;
        }
        return budgetPerPerson.multiply(BigDecimal.valueOf(travelers)).setScale(2, RoundingMode.HALF_UP);
    }

    private String extractDestination(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Matcher destinationHint = DESTINATION_HINT_PATTERN.matcher(text);
        if (destinationHint.find()) {
            return destinationHint.group(1);
        }

        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains("行程") && trimmed.length() <= 30) {
                return trimmed.replace("行程", "").replace("计划", "").trim();
            }
        }
        return null;
    }

    private String extractTitle(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                trimmed = trimmed.replaceFirst("^#+", "").trim();
            }
            if (trimmed.length() <= 36 && (trimmed.contains("行程") || trimmed.contains("旅游") || trimmed.contains("旅行"))) {
                return trimmed;
            }
        }
        return null;
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return null;
    }

    private void saveAiDayPlans(Long itineraryId, List<AiItineraryCreateRequest.AiDayPlanRequest> dayPlans) {
        if (itineraryId == null || dayPlans == null || dayPlans.isEmpty()) {
            return;
        }

        for (AiItineraryCreateRequest.AiDayPlanRequest dayPlan : dayPlans) {
            if (dayPlan == null || isBlank(dayPlan.getName())) {
                continue;
            }

            ItineraryPlace place = new ItineraryPlace();
            place.setItineraryId(itineraryId);
            place.setDayNumber(dayPlan.getDayNumber() != null && dayPlan.getDayNumber() > 0 ? dayPlan.getDayNumber() : 1);
            place.setTime(normalizePlaceTime(dayPlan.getTime()));
            place.setName(dayPlan.getName().trim());
            place.setType(normalizePlaceType(dayPlan.getType()));
            place.setImageUrl(safeTrim(dayPlan.getImageUrl()));
            place.setDistance(safeTrim(dayPlan.getDistance()));
            place.setDuration(safeTrim(dayPlan.getDuration()));
            place.setTags(normalizeTags(dayPlan.getTags()));

            itineraryPlaceMapper.insert(place);
        }
    }

    private void saveAiRecommendations(
            List<AiItineraryCreateRequest.AiRecommendationSaveRequest> recommendations,
            String fallbackLocation,
            Long userId
    ) {
        if (recommendations == null || recommendations.isEmpty()) {
            return;
        }

        for (AiItineraryCreateRequest.AiRecommendationSaveRequest item : recommendations) {
            if (item == null || isBlank(item.getTitle())) {
                continue;
            }

            Recommendation recommendation = new Recommendation();
            recommendation.setTitle(item.getTitle().trim());
            recommendation.setDescription(firstNonBlank(item.getDescription(), "AI助手生成的热门推荐"));
            recommendation.setImageUrl(safeTrim(item.getImageUrl()));
            recommendation.setPrice(BigDecimal.valueOf(item.getPrice() != null && item.getPrice() > 0 ? item.getPrice() : 0));
            recommendation.setRating(BigDecimal.valueOf(item.getRating() != null && item.getRating() > 0 ? item.getRating() : 4.5));
            recommendation.setReviewCount(item.getReviewCount() != null && item.getReviewCount() > 0 ? item.getReviewCount() : 100);
            recommendation.setTags(normalizeTags(item.getTags()));
            recommendation.setLocation(firstNonBlank(item.getLocation(), fallbackLocation, "未知城市"));
            recommendation.setSource("ai_itinerary_save:user_" + userId);
            recommendation.setIsActive(1);
            recommendation.setScore(buildRecommendationScore(recommendation.getRating(), recommendation.getReviewCount()));

            recommendationMapper.insert(recommendation);
        }
    }

    private String normalizePlaceTime(String rawTime) {
        if (rawTime == null || rawTime.isBlank()) {
            return "09:00";
        }
        String trimmed = rawTime.trim();
        if (trimmed.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            return trimmed;
        }
        Matcher matcher = Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(trimmed);
        if (!matcher.find()) {
            return "09:00";
        }

        int hour = Integer.parseInt(matcher.group(1));
        int minute = Integer.parseInt(matcher.group(2));
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return "09:00";
        }
        return String.format("%02d:%02d", hour, minute);
    }

    private String normalizePlaceType(String type) {
        if (type == null || type.isBlank()) {
            return "attraction";
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if ("attraction".equals(normalized) || "restaurant".equals(normalized) || "hotel".equals(normalized)) {
            return normalized;
        }
        if (normalized.contains("餐") || normalized.contains("food")) {
            return "restaurant";
        }
        if (normalized.contains("酒店") || normalized.contains("hotel")) {
            return "hotel";
        }
        return "attraction";
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        List<String> result = tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        return result.isEmpty() ? null : result;
    }

    private BigDecimal buildRecommendationScore(BigDecimal rating, Integer reviewCount) {
        double safeRating = rating != null ? rating.doubleValue() : 4.5;
        int safeReviewCount = reviewCount != null ? Math.max(1, reviewCount) : 100;
        double score = safeRating * 20 + Math.log10(safeReviewCount) * 10;
        return BigDecimal.valueOf(score);
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private String safeTrim(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
