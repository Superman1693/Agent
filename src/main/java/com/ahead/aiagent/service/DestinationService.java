package com.ahead.aiagent.service;

import com.ahead.aiagent.model.vo.DestinationVO;

import java.util.List;

/**
 * 目的地服务接口
 */
public interface DestinationService {

    /**
     * 获取精选目的地列表
     */
    List<DestinationVO> getFeaturedDestinations(Integer limit);

    /**
     * 获取目的地详情
     */
    DestinationVO getDestinationDetail(Long id);

    /**
     * 刷新精选目的地数据（定时任务调用）
     */
    void refreshDestinations();
}
