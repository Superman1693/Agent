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
     * 每月1号凌晨2点执行（正式周期）
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "${app.job.recommendation-monthly-cron:0 0 2 1 * ?}")
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
     * 修复 E10/O8：原「每天 3 点」测试任务与每月正式任务重复，导致每天额外执行一次昂贵任务。
     * 改为由 app.job.test-daily-cron 控制，默认值 "-" 表示禁用（Spring 的 CRON_DISABLED）。
     */
    @Scheduled(cron = "${app.job.test-daily-cron:-}")
    public void dailyRefresh() {
        log.info("定时任务：每日刷新热门推荐（测试任务）");

        try {
            recommendationService.refreshRecommendations();
            log.info("定时任务：每日刷新成功");

        } catch (Exception e) {
            log.error("定时任务：每日刷新失败", e);
        }
    }
}
