package com.app.order.controller;

import com.app.order.api.ApiHeaders;
import com.app.order.dto.CreateOrderRequest;
import com.app.order.dto.OrderResponse;
import com.app.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(OrderController.ORDERS_PATH)
public class OrderController {

    public static final String ORDERS_PATH = "/api/orders";

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestHeader(ApiHeaders.USER_ID) UUID userId,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponse response = orderService.create(userId, request);
        return ResponseEntity
                .created(URI.create(ORDERS_PATH + "/" + response.id()))
                .body(response);
    }
}
