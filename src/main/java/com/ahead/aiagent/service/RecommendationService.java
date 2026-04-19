package com.ahead.aiagent.service;

import com.ahead.aiagent.model.dto.RecommendationQueryRequest;
import com.ahead.aiagent.model.vo.RecommendationPageVO;

/**
 * 热门推荐服务接口
 */
public interface RecommendationService {

    /**
     * 分页查询热门推荐
     *
     * @param request 查询请求
     * @return 分页结果
     */
    RecommendationPageVO getHotRecommendations(RecommendationQueryRequest request);

    /**
     * 根据用户位置获取附近热门景点推荐（Top 3）
     *
     * @param userLocation 用户位置（城市名称，如"北京"、"上海"）
     * @return 推荐列表
     */
    RecommendationPageVO getNearbyRecommendations(String userLocation);

    /**
     * 刷新热门推荐数据（定时任务调用）
     * 采集候选数据 -> 清洗去重 -> 计算分数 -> 写入 MySQL
     */
    void refreshRecommendations();
}
