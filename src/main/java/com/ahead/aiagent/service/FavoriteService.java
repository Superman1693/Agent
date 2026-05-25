package com.ahead.aiagent.service;

import com.ahead.aiagent.model.dto.FavoriteAddRequest;
import com.ahead.aiagent.model.vo.FavoriteListVO;
import com.ahead.aiagent.model.vo.FavoriteVO;

public interface FavoriteService {

    FavoriteVO addFavorite(FavoriteAddRequest request, Long userId);

    boolean removeFavorite(Long targetId, String targetType, Long userId);

    FavoriteListVO listFavorites(String targetType, Integer page, Integer size, Long userId);
}
