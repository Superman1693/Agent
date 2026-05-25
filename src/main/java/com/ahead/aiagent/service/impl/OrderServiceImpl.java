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
import com.ahead.aiagent.service.OrderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private final OrderMapper orderMapper;
    private final DestinationMapper destinationMapper;
    private final RecommendationMapper recommendationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateOrderVO createOrder(OrderCreateRequest request, Long userId) {
        if (!"destination".equals(request.getProductType()) && !"package".equals(request.getProductType())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "productType 仅支持 destination 或 package");
        }

        ProductSnapshot snapshot = resolveProductSnapshot(request.getProductType(), request.getProductId());

        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNo(generateOrderNo());
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

        orderMapper.insert(order);

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
        if ("completed".equals(order.getStatus()) || "refund".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前订单状态不可取消");
        }

        order.setStatus("refund");
        if (StrUtil.isNotBlank(reason)) {
            order.setCancelReason(reason.trim());
        }
        return orderMapper.updateById(order) > 0;
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
        if (!"pending_payment".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前订单状态不可支付");
        }

        order.setStatus("pending_travel");
        return orderMapper.updateById(order) > 0;
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
        if ("refund".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前订单已在退款/售后处理中");
        }
        if (!"pending_travel".equals(order.getStatus()) && !"completed".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前订单状态暂不支持售后申请");
        }

        order.setStatus("refund");
        order.setCancelReason(StrUtil.isBlank(reason) ? "用户申请退款/售后" : reason.trim());
        return orderMapper.updateById(order) > 0;
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

        Recommendation recommendation = recommendationMapper.selectById(productId);
        if (recommendation == null || !Integer.valueOf(1).equals(recommendation.getIsActive())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "推荐商品不存在");
        }

        BigDecimal amount = recommendation.getPrice() == null ? BigDecimal.ZERO : recommendation.getPrice();
        return new ProductSnapshot(recommendation.getTitle(), recommendation.getImageUrl(), amount);
    }

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "OD" + timestamp + random;
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
