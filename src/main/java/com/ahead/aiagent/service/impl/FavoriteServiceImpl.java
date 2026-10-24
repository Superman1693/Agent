package com.ahead.aiagent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.mapper.DestinationMapper;
import com.ahead.aiagent.mapper.FavoriteMapper;
import com.ahead.aiagent.mapper.RecommendationMapper;
import com.ahead.aiagent.model.dto.FavoriteAddRequest;
import com.ahead.aiagent.model.entity.Destination;
import com.ahead.aiagent.model.entity.Favorite;
import com.ahead.aiagent.model.entity.Recommendation;
import com.ahead.aiagent.model.vo.FavoriteListVO;
import com.ahead.aiagent.model.vo.FavoriteVO;
import com.ahead.aiagent.service.FavoriteService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final FavoriteMapper favoriteMapper;
    private final DestinationMapper destinationMapper;
    private final RecommendationMapper recommendationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteVO addFavorite(FavoriteAddRequest request, Long userId) {
        String targetType = normalizeTargetType(request.getTargetType());

        Favorite existed = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetId, request.getTargetId())
                .eq(Favorite::getTargetType, targetType));
        if (existed != null) {
            return toVO(existed);
        }

        ProductSnapshot snapshot = resolveSnapshot(targetType, request.getTargetId());

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setTargetId(request.getTargetId());
        favorite.setTargetType(targetType);
        favorite.setTargetName(snapshot.name());
        favorite.setTargetImage(snapshot.image());
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            // 修复 E31/D19：并发重复收藏会撞 UNIQUE KEY uniq_user_target。
            // 改为幂等处理——回查已存在记录并直接返回，避免用户快速双击收到 500。
            Favorite concurrent = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                    .eq(Favorite::getUserId, userId)
                    .eq(Favorite::getTargetId, request.getTargetId())
                    .eq(Favorite::getTargetType, targetType));
            if (concurrent != null) {
                return toVO(concurrent);
            }
            throw e;
        }

        return toVO(favorite);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeFavorite(Long targetId, String targetType, Long userId) {
        String normalizedType = normalizeTargetType(targetType);
        return favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetId, targetId)
                .eq(Favorite::getTargetType, normalizedType)) > 0;
    }

    @Override
    public FavoriteListVO listFavorites(String targetType, Integer page, Integer size, Long userId) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safeSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUserId, userId);
        if (StrUtil.isNotBlank(targetType)) {
            wrapper.eq(Favorite::getTargetType, normalizeTargetType(targetType));
        }
        wrapper.orderByDesc(Favorite::getCreateTime);

        Page<Favorite> pageResult = favoriteMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        List<FavoriteVO> list = pageResult.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new FavoriteListVO(list, pageResult.getTotal());
    }

    private String normalizeTargetType(String targetType) {
        if (StrUtil.isBlank(targetType)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "targetType 不能为空");
        }

        String normalized = targetType.trim().toLowerCase();
        if (!"destination".equals(normalized) && !"recommendation".equals(normalized)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "targetType 仅支持 destination 或 recommendation");
        }
        return normalized;
    }

    private ProductSnapshot resolveSnapshot(String targetType, Long targetId) {
        if ("destination".equals(targetType)) {
            Destination destination = destinationMapper.selectById(targetId);
            if (destination == null || !Integer.valueOf(1).equals(destination.getIsActive())) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "收藏目标不存在");
            }
            return new ProductSnapshot(destination.getName(), destination.getImageUrl());
        }

        Recommendation recommendation = recommendationMapper.selectById(targetId);
        if (recommendation == null || !Integer.valueOf(1).equals(recommendation.getIsActive())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "收藏目标不存在");
        }
        return new ProductSnapshot(recommendation.getTitle(), recommendation.getImageUrl());
    }

    private FavoriteVO toVO(Favorite favorite) {
        FavoriteVO vo = new FavoriteVO();
        vo.setId(favorite.getId());
        vo.setTargetId(favorite.getTargetId());
        vo.setTargetType(favorite.getTargetType());
        vo.setTargetName(favorite.getTargetName());
        vo.setTargetImage(favorite.getTargetImage());
        vo.setCreateTime(favorite.getCreateTime());
        return vo;
    }

    private record ProductSnapshot(String name, String image) {
    }
}
