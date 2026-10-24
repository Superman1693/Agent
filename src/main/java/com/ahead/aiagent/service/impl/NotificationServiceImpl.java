package com.ahead.aiagent.service.impl;

import com.ahead.aiagent.mapper.NotificationMapper;
import com.ahead.aiagent.model.entity.Notification;
import com.ahead.aiagent.model.vo.NotificationListVO;
import com.ahead.aiagent.model.vo.NotificationVO;
import com.ahead.aiagent.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 消息通知服务实现（修复 E23）
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final NotificationMapper notificationMapper;

    @Override
    public NotificationListVO listNotifications(boolean unreadOnly, Integer page, Integer size, Long userId) {
        int safePage = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int safeSize = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        if (unreadOnly) {
            wrapper.eq(Notification::getIsRead, 0);
        }
        wrapper.orderByDesc(Notification::getCreateTime);

        Page<Notification> pageResult = notificationMapper.selectPage(new Page<>(safePage, safeSize), wrapper);

        List<NotificationVO> list = pageResult.getRecords().stream()
                .map(this::toVO)
                .toList();

        Long unread = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));

        return new NotificationListVO(list, unread == null ? 0L : unread, pageResult.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markRead(List<Long> notificationIds, Long userId) {
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1);
        if (notificationIds != null && !notificationIds.isEmpty()) {
            wrapper.in(Notification::getId, notificationIds);
        }
        notificationMapper.update(null, wrapper);
        return true;
    }

    private NotificationVO toVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setType(notification.getType());
        vo.setIsRead(Integer.valueOf(1).equals(notification.getIsRead()));
        vo.setCreateTime(notification.getCreateTime());
        return vo;
    }
}
