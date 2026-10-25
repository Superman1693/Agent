package com.ahead.aiagent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.mapper.DestinationMapper;
import com.ahead.aiagent.mapper.OrderMapper;
import com.ahead.aiagent.mapper.RecommendationMapper;
import com.ahead.aiagent.model.dto.OrderCreateRequest;
import com.ahead.aiagent.model.entity.Destination;
import com.ahead.aiagent.model.entity.Order;
import com.ahead.aiagent.model.entity.Recommendation;
import com.ahead.aiagent.model.vo.CreateOrderVO;
import com.ahead.aiagent.model.vo.OrderDetailVO;
import com.ahead.aiagent.model.vo.OrderListVO;
import com.ahead.aiagent.model.vo.OrderVO;
import com.ahead.aiagent.service.NotificationService;
import com.ahead.aiagent.service.OrderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private final OrderMapper orderMapper;
    private final DestinationMapper destinationMapper;
    private final RecommendationMapper recommendationMapper;
    /** 修复 O22：订单状态流转时给用户写站内通知 */
    private final NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateOrderVO createOrder(OrderCreateRequest request, Long userId) {
        if (!"destination".equals(request.getProductType()) && !"package".equals(request.getProductType())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "productType 仅支持 destination 或 package");
        }

        ProductSnapshot snapshot = resolveProductSnapshot(request.getProductType(), request.getProductId());

        Order order = new Order();
        order.setUserId(userId);
        order.setProductId(request.getProductId());
        order.setProductType(request.getProductType());
        order.setProductName(snapshot.productName());
        order.setProductImage(snapshot.productImage());
        order.setTotalAmount(snapshot.totalAmount());
        order.setTravelers(request.getTravelers());
        order.setStartDate(request.getStartDate());
        order.setContactName(request.getContactName());
        order.setContactPhone(request.getContactPhone());
        order.setStatus("pending_payment");

        // 修复：order_no 有唯一键，秒级时间戳+4 位随机位在并发/批量下单时可碰撞导致 insert 直接失败。
        // 改为：随机位提到 6 位，撞唯一键（DuplicateKeyException）时换号重试，最多 3 次。
        DuplicateKeyException lastConflict = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            order.setOrderNo(generateOrderNo());
            try {
                orderMapper.insert(order);
                lastConflict = null;
                break;
            } catch (DuplicateKeyException e) {
                lastConflict = e;
                log.warn("订单号冲突，换号重试（第 {} 次）: {}", attempt, order.getOrderNo());
            }
        }
        if (lastConflict != null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "订单创建失败，请稍后重试");
        }

        // 下单成功即通知用户尽快支付（内容含订单号，type=order）
        notificationService.notify(userId, "下单成功，请尽快完成支付",
                "您的订单 " + order.getOrderNo() + " 已创建成功，请尽快完成支付。", "order");

        CreateOrderVO vo = new CreateOrderVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }

    @Override
    public OrderListVO listOrders(String status, Integer page, Integer size, Long userId) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safeSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId);
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getCreateTime);

        Page<Order> pageResult = orderMapper.selectPage(new Page<>(safePage, safeSize), wrapper);

        List<OrderVO> list = pageResult.getRecords().stream()
                .map(this::toOrderVO)
                .collect(Collectors.toList());

        return new OrderListVO(list, pageResult.getTotal());
    }

    @Override
    public OrderDetailVO getOrderDetail(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该订单");
        }

        return toOrderDetailVO(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long orderId, String reason, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限取消该订单");
        }

        // 修复 E11/D13：由「读-判断-写」改为带状态条件的原子更新，避免并发下取消与支付互相覆盖。
        // 修复 E33/D14：取消使用独立状态 cancelled，与售后 refund 区分，便于统计取消率与售后率。
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .in(Order::getStatus, "pending_payment", "pending_travel")
                .set(Order::getStatus, "cancelled")
                .set(StrUtil.isNotBlank(reason), Order::getCancelReason, reason == null ? null : reason.trim()));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前订单状态不可取消");
        }
        // 修复 O22：取消成功后通知用户（内容含订单号）
        notificationService.notify(userId, "订单已取消",
                "您的订单 " + order.getOrderNo() + " 已取消" + (StrUtil.isBlank(reason) ? "。" : "，原因：" + reason.trim()),
                "order");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean payOrder(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限支付该订单");
        }

        // 修复 E11/D13：仅当状态仍为 pending_payment 时才更新，保证支付与取消互斥
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, "pending_payment")
                .set(Order::getStatus, "pending_travel"));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前订单状态不可支付");
        }
        // 修复 O22：支付成功后通知用户（内容含订单号）
        notificationService.notify(userId, "支付成功",
                "您的订单 " + order.getOrderNo() + " 已支付成功，祝您旅途愉快！", "order");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean applyAfterSale(Long orderId, String reason, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限申请该订单售后");
        }

        // 修复 E11/D13：带状态条件的原子更新，防止重复提交与状态漂移
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .in(Order::getStatus, "pending_travel", "completed")
                .set(Order::getStatus, "refund")
                .set(Order::getCancelReason, StrUtil.isBlank(reason) ? "用户申请退款/售后" : reason.trim()));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前订单状态暂不支持售后申请");
        }
        // 修复 O22：售后申请提交后通知用户（内容含订单号）
        notificationService.notify(userId, "售后申请已提交",
                "您的订单 " + order.getOrderNo() + " 售后申请已提交，我们将尽快为您处理。", "order");
        return true;
    }

    private ProductSnapshot resolveProductSnapshot(String productType, Long productId) {
        if ("destination".equals(productType)) {
            Destination destination = destinationMapper.selectById(productId);
            if (destination == null || !Integer.valueOf(1).equals(destination.getIsActive())) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "目的地商品不存在");
            }

            BigDecimal amount = destination.getPrice() == null ? BigDecimal.ZERO : destination.getPrice();
            return new ProductSnapshot(destination.getName(), destination.getImageUrl(), amount);
        }

        if ("package".equals(productType)) {
            // 修复 E22：显式区分 package 分支。当前套餐商品复用 recommendation 表
            // （尚无独立 package 表）；未来新增套餐表时应在此处改为查套餐表，避免静默查错表。
            Recommendation recommendation = recommendationMapper.selectById(productId);
            if (recommendation == null || !Integer.valueOf(1).equals(recommendation.getIsActive())) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "推荐商品不存在");
            }

            BigDecimal amount = recommendation.getPrice() == null ? BigDecimal.ZERO : recommendation.getPrice();
            return new ProductSnapshot(recommendation.getTitle(), recommendation.getImageUrl(), amount);
        }

        throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的 productType");
    }

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        // 修复：随机位从 4 位提到 6 位（含前导零），降低并发撞号概率
        int random = ThreadLocalRandom.current().nextInt(1_000_000);
        return "OD" + timestamp + String.format("%06d", random);
    }

    private OrderVO toOrderVO(Order order) {
        OrderVO vo = new OrderVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setProductName(order.getProductName());
        vo.setProductImage(order.getProductImage());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }

    private OrderDetailVO toOrderDetailVO(Order order) {
        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setProductName(order.getProductName());
        vo.setProductImage(order.getProductImage());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setCreateTime(order.getCreateTime());
        vo.setTravelers(order.getTravelers());
        vo.setStartDate(order.getStartDate());
        vo.setContactName(order.getContactName());
        vo.setContactPhone(order.getContactPhone());
        vo.setCancelReason(order.getCancelReason());
        return vo;
    }

    private record ProductSnapshot(String productName, String productImage, BigDecimal totalAmount) {
    }
}
