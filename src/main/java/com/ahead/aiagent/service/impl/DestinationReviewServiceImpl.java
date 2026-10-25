package com.ahead.aiagent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.mapper.DestinationMapper;
import com.ahead.aiagent.mapper.DestinationReviewMapper;
import com.ahead.aiagent.mapper.UserMapper;
import com.ahead.aiagent.model.dto.DestinationReviewSaveRequest;
import com.ahead.aiagent.model.dto.DestinationReviewUpdateRequest;
import com.ahead.aiagent.model.entity.Destination;
import com.ahead.aiagent.model.entity.DestinationReview;
import com.ahead.aiagent.model.entity.User;
import com.ahead.aiagent.model.vo.DestinationReviewPageVO;
import com.ahead.aiagent.model.vo.DestinationReviewVO;
import com.ahead.aiagent.service.DestinationReviewService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 功能6：目的地用户评价服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DestinationReviewServiceImpl implements DestinationReviewService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private final DestinationReviewMapper destinationReviewMapper;
    private final DestinationMapper destinationMapper;
    private final UserMapper userMapper;

    @Override
    public DestinationReviewVO saveReview(DestinationReviewSaveRequest request, Long userId) {
        // 目的地有效性校验
        Destination destination = destinationMapper.selectById(request.getDestinationId());
        if (destination == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "目的地不存在");
        }

        // 同用户同目的地已存在则更新（幂等）
        DestinationReview existing = destinationReviewMapper.selectOne(new LambdaQueryWrapper<DestinationReview>()
                .eq(DestinationReview::getUserId, userId)
                .eq(DestinationReview::getDestinationId, request.getDestinationId()));
        if (existing != null) {
            existing.setRating(request.getRating());
            existing.setContent(StrUtil.blankToDefault(trimContent(request.getContent()), existing.getContent()));
            destinationReviewMapper.updateById(existing);
            return toVO(existing, null, userId, null);
        }

        DestinationReview review = new DestinationReview();
        review.setUserId(userId);
        review.setDestinationId(request.getDestinationId());
        review.setRating(request.getRating());
        review.setContent(trimContent(request.getContent()));
        destinationReviewMapper.insert(review);

        User user = userMapper.selectById(userId);
        return toVO(review, user, userId, null);
    }

    @Override
    public DestinationReviewVO updateReview(Long id, DestinationReviewUpdateRequest request, Long userId) {
        DestinationReview review = requireOwnedReview(id, userId);
        review.setRating(request.getRating());
        if (request.getContent() != null) {
            review.setContent(trimContent(request.getContent()));
        }
        destinationReviewMapper.updateById(review);
        return toVO(review, null, userId, null);
    }

    @Override
    public boolean deleteReview(Long id, Long userId) {
        requireOwnedReview(id, userId);
        return destinationReviewMapper.deleteById(id) > 0;
    }

    @Override
    public DestinationReviewPageVO listReviews(Long destinationId, Integer page, Integer size, Long userId) {
        if (destinationId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "destinationId 不能为空");
        }
        int safePage = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int safeSize = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        Page<DestinationReview> pageResult = destinationReviewMapper.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<DestinationReview>()
                        .eq(DestinationReview::getDestinationId, destinationId)
                        .orderByDesc(DestinationReview::getCreateTime));

        List<DestinationReview> records = pageResult.getRecords();
        // 批量联 user 表填充 username/avatar，避免逐条查询造成 N+1
        Map<Long, User> userMap = loadUsers(records);

        List<DestinationReviewVO> list = records.stream()
                .map(review -> {
                    User user = userMap.get(review.getUserId());
                    // mine：仅当请求携带有效登录态且 userId 相等时为 true（公开路径 AuthContext 无值为 false）
                    Boolean mine = userId != null && userId.equals(review.getUserId());
                    return toVO(review, user, userId, mine);
                })
                .collect(Collectors.toList());

        return new DestinationReviewPageVO(list, pageResult.getTotal(), safePage, safeSize);
    }

    /** 批量加载评价作者信息 */
    private Map<Long, User> loadUsers(List<DestinationReview> records) {
        List<Long> userIds = records.stream()
                .map(DestinationReview::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Collection<User> users = userMapper.selectBatchIds(userIds);
        return users.stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** 查询评价并校验归属，不存在抛 NOT_FOUND，非本人抛 NO_AUTH */
    private DestinationReview requireOwnedReview(Long id, Long userId) {
        DestinationReview review = destinationReviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "评价不存在");
        }
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作该评价");
        }
        return review;
    }

    private String trimContent(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private DestinationReviewVO toVO(DestinationReview review, User user, Long currentUserId, Boolean mine) {
        DestinationReviewVO vo = new DestinationReviewVO();
        vo.setId(review.getId());
        vo.setDestinationId(review.getDestinationId());
        vo.setUserId(review.getUserId());
        vo.setUsername(user == null ? null : user.getUsername());
        vo.setAvatar(user == null ? null : user.getAvatarUrl());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setCreateTime(review.getCreateTime());
        // mine 缺省：作者本人场景（保存/更新返回值）恒为 true
        vo.setMine(mine != null ? mine : Objects.equals(currentUserId, review.getUserId()));
        return vo;
    }
}
