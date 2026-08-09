package com.app.order.controller;

import com.app.order.dto.OrderResponse;
import com.app.order.dto.PageResponse;
import com.app.order.model.OrderStatus;
import com.app.order.service.OrderQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderQueryService orderQueryService;

    @GetMapping
    public PageResponse<OrderResponse> findAll(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return orderQueryService.findAdminOrders(userId, status, page, size);
    }

    @GetMapping("/{orderId}")
    public OrderResponse findById(@PathVariable UUID orderId) {
        return orderQueryService.findAdminOrder(orderId);
    }
}
