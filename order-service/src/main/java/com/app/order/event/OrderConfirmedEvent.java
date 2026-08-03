package com.app.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Builder;

@Builder
public record OrderConfirmedEvent(
        UUID messageId,
        int eventVersion,
        OrderEventType eventType,
        UUID orderId,
        UUID userId,
        Long reservationId,
        BigDecimal totalPrice,
        List<OrderConfirmedItem> items,
        Instant occurredAt
) implements OutboxPayload {
}
