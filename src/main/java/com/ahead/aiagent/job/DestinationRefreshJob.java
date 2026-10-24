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
     * 每周一凌晨 2 点执行（正式周期）
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "${app.job.destination-weekly-cron:0 0 2 ? * MON}")
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
     * 修复 E10/O8：原「每天 3 点」测试任务与周一正式任务重复，导致一周实际执行 8 次昂贵任务。
     * 改为由 app.job.test-daily-cron 控制，默认值 "-" 表示禁用（Spring 的 CRON_DISABLED）。
     */
    @Scheduled(cron = "${app.job.test-daily-cron:-}")
    public void dailyRefresh() {
        log.info("定时任务：每日刷新精选目的地（测试任务）");
        try {
            destinationService.refreshDestinations();
            log.info("定时任务：每日刷新成功");
        } catch (Exception e) {
            log.error("定时任务：每日刷新失败", e);
        }
    }
}
