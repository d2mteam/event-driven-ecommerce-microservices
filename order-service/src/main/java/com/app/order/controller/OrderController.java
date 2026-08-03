package com.app.order.controller;

import com.app.order.api.ApiHeaders;
import com.app.order.dto.CreateOrderRequest;
import com.app.order.dto.OrderResponse;
import com.app.order.dto.PageResponse;
import com.app.order.dto.PaymentResponse;
import com.app.order.service.IdempotentOrderService;
import com.app.order.service.OrderPaymentService;
import com.app.order.service.OrderQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(OrderController.ORDERS_PATH)
public class OrderController {

    public static final String ORDERS_PATH = "/api/orders";

    private final IdempotentOrderService orderService;
    private final OrderQueryService orderQueryService;
    private final OrderPaymentService orderPaymentService;

    @GetMapping("/{orderId}")
    public OrderResponse findById(
            @RequestHeader(ApiHeaders.USER_ID) UUID userId,
            @PathVariable UUID orderId
    ) {
        return orderQueryService.findByIdAndUserId(orderId, userId);
    }

    @GetMapping
    public PageResponse<OrderResponse> findAll(
            @RequestHeader(ApiHeaders.USER_ID) UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return orderQueryService.findAllByUserId(
                userId,
                page,
                size
        );
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestHeader(ApiHeaders.USER_ID) UUID userId,
            @RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponse response = orderService.create(
                userId,
                idempotencyKey,
                request
        );
        return ResponseEntity
                .created(URI.create(ORDERS_PATH + "/" + response.id()))
                .body(response);
    }

    @PostMapping("/{orderId}/payments")
    public PaymentResponse createPayment(
            @RequestHeader(ApiHeaders.USER_ID) UUID userId,
            @PathVariable UUID orderId
    ) {
        return orderPaymentService.create(userId, orderId);
    }
}
