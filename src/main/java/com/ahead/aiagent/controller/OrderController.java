package com.ahead.aiagent.controller;

import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.common.ResultUtils;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.model.dto.OrderCancelRequest;
import com.ahead.aiagent.model.dto.OrderCreateRequest;
import com.ahead.aiagent.model.dto.OrderAfterSaleRequest;
import com.ahead.aiagent.model.vo.CreateOrderVO;
import com.ahead.aiagent.model.vo.OrderDetailVO;
import com.ahead.aiagent.model.vo.OrderListVO;
import com.ahead.aiagent.security.AuthContext;
import com.ahead.aiagent.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "订单管理")
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "创建订单")
    @PostMapping("/create")
    public BaseResponse<CreateOrderVO> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        CreateOrderVO order = orderService.createOrder(request, userId);
        return ResultUtils.success(order, "ok");
    }

    @Operation(summary = "获取订单列表")
    @GetMapping("/list")
    public BaseResponse<OrderListVO> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        OrderListVO result = orderService.listOrders(status, page, size, userId);
        return ResultUtils.success(result, "ok");
    }

    @Operation(summary = "获取订单详情")
    @GetMapping("/{orderId}")
    public BaseResponse<OrderDetailVO> getOrderDetail(@PathVariable Long orderId) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        OrderDetailVO detail = orderService.getOrderDetail(orderId, userId);
        return ResultUtils.success(detail, "ok");
    }

    @Operation(summary = "取消订单")
    @PostMapping("/{orderId}/cancel")
    public BaseResponse<Boolean> cancelOrder(
            @PathVariable Long orderId,
            @RequestBody(required = false) OrderCancelRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        boolean result = orderService.cancelOrder(orderId, request == null ? null : request.getReason(), userId);
        return ResultUtils.success(result, "ok");
    }

    @Operation(summary = "订单支付")
    @PostMapping("/{orderId}/pay")
    public BaseResponse<Boolean> payOrder(@PathVariable Long orderId) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        boolean result = orderService.payOrder(orderId, userId);
        return ResultUtils.success(result, "ok");
    }

    @Operation(summary = "申请退款/售后")
    @PostMapping("/{orderId}/after-sale")
    public BaseResponse<Boolean> applyAfterSale(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderAfterSaleRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        boolean result = orderService.applyAfterSale(orderId, request.getReason(), userId);
        return ResultUtils.success(result, "ok");
    }
}
