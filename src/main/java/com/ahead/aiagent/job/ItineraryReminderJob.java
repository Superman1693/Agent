package com.ahead.aiagent.job;

import com.ahead.aiagent.mapper.ItineraryMapper;
import com.ahead.aiagent.model.entity.Itinerary;
import com.ahead.aiagent.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 行程出发前提醒定时任务：扫描 3 天后出发且尚未提醒的行程，逐条发送站内通知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItineraryReminderJob {

    /** 提前提醒天数 */
    private static final int REMIND_AHEAD_DAYS = 3;

    private final ItineraryMapper itineraryMapper;
    private final NotificationService notificationService;

    /**
     * 每天早上 8 点执行（可通过 app.jobs.reminder-cron 覆盖）
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "${app.jobs.reminder-cron:0 0 8 * * ?}")
    public void remindUpcomingItineraries() {
        LocalDate remindDate = LocalDate.now().plusDays(REMIND_AHEAD_DAYS);
        log.info("定时任务：开始扫描 {} 出发的行程提醒", remindDate);
        try {
            List<Itinerary> itineraries = itineraryMapper.selectList(new LambdaQueryWrapper<Itinerary>()
                    .eq(Itinerary::getStartDate, remindDate)
                    .eq(Itinerary::getReminderSent, 0));

            for (Itinerary itinerary : itineraries) {
                // 逐条 try/catch，单条失败不中断其余提醒
                try {
                    notificationService.notify(itinerary.getUserId(), "行程即将出发",
                            "您的行程「" + itinerary.getTitle() + "」将于 " + formatSafe(itinerary) + " 出发，"
                                    + "目的地：" + itinerary.getDestination() + "，请提前做好出行准备。",
                            "reminder");
                    Itinerary update = new Itinerary();
                    update.setId(itinerary.getId());
                    update.setReminderSent(1);
                    itineraryMapper.updateById(update);
                } catch (Exception e) {
                    log.error("行程提醒发送失败: itineraryId={}", itinerary.getId(), e);
                }
            }
            log.info("定时任务：行程提醒扫描完成，共处理 {} 条", itineraries.size());
        } catch (Exception e) {
            log.error("定时任务：行程提醒扫描失败", e);
        }
    }

    /** 出发日期兜底格式化，避免异常日期导致整轮任务失败 */
    private String formatSafe(Itinerary itinerary) {
        LocalDate startDate = itinerary.getStartDate();
        return startDate == null ? "近期" : startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}
