package com.ahead.aiagent.job;

import com.ahead.aiagent.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 热门推荐刷新定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationRefreshJob {

    private final RecommendationService recommendationService;

    /**
     * 每月1号凌晨2点执行
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void refreshRecommendations() {
        log.info("定时任务：开始刷新热门推荐");

        try {
            recommendationService.refreshRecommendations();
            log.info("定时任务：热门推荐刷新成功");

        } catch (Exception e) {
            log.error("定时任务：热门推荐刷新失败", e);
        }
    }

    /**
     * 测试用：每天凌晨3点执行（可根据需要删除）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyRefresh() {
        log.info("定时任务：每日刷新热门推荐");

        try {
            recommendationService.refreshRecommendations();
            log.info("定时任务：每日刷新成功");

        } catch (Exception e) {
            log.error("定时任务：每日刷新失败", e);
        }
    }
}
