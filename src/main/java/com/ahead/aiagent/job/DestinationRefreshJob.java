package com.ahead.aiagent.job;

import com.ahead.aiagent.service.DestinationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 目的地刷新定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DestinationRefreshJob {

    private final DestinationService destinationService;

    /**
     * 每周一凌晨 2 点执行
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 2 ? * MON")
    public void refreshDestinations() {
        log.info("定时任务：开始刷新精选目的地");
        try {
            destinationService.refreshDestinations();
            log.info("定时任务：精选目的地刷新成功");
        } catch (Exception e) {
            log.error("定时任务：精选目的地刷新失败", e);
        }
    }

    /**
     * 手动触发刷新（用于测试）
     * 每天凌晨 3 点执行一次（可根据需要调整或删除）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyRefresh() {
        log.info("定时任务：每日刷新精选目的地");
        try {
            destinationService.refreshDestinations();
            log.info("定时任务：每日刷新成功");
        } catch (Exception e) {
            log.error("定时任务：每日刷新失败", e);
        }
    }
}
