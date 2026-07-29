package com.app.order.dto;

import com.app.order.model.OrderStatus;
import com.app.order.model.OrderFailureReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID userId,
        Long reservationId,
        OrderStatus status,
        OrderFailureReason failureReason,
        BigDecimal totalPrice,
        List<OrderItemResponse> items,
        Instant createdAt
) {
}
