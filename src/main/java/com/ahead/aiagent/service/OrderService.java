package com.ahead.aiagent.service;

import com.ahead.aiagent.model.dto.OrderCreateRequest;
import com.ahead.aiagent.model.vo.CreateOrderVO;
import com.ahead.aiagent.model.vo.OrderDetailVO;
import com.ahead.aiagent.model.vo.OrderListVO;

public interface OrderService {

    CreateOrderVO createOrder(OrderCreateRequest request, Long userId);

    OrderListVO listOrders(String status, Integer page, Integer size, Long userId);

    OrderDetailVO getOrderDetail(Long orderId, Long userId);

    boolean cancelOrder(Long orderId, String reason, Long userId);

    boolean payOrder(Long orderId, Long userId);

    boolean applyAfterSale(Long orderId, String reason, Long userId);
}
