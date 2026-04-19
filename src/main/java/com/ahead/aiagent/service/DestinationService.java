package com.ahead.aiagent.service;

import com.ahead.aiagent.model.vo.DestinationVO;

import java.util.List;

/**
 * 目的地服务接口
 */
public interface DestinationService {

    /**
     * 获取精选目的地列表（Top 3）
     */
    List<DestinationVO> getFeaturedDestinations();

    /**
     * 刷新精选目的地数据（定时任务调用）
     */
    void refreshDestinations();
}
