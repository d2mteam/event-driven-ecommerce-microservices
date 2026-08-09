package com.app.order.event;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelledEvent(
        UUID messageId,
        int eventVersion,
        OrderEventType eventType,
        UUID orderId,
        UUID userId,
        Long reservationId,
        Instant occurredAt
) implements OutboxPayload {

    public OrderCancelledEvent {
        if (messageId == null
                || eventType != OrderEventType.ORDER_CANCELLED
                || orderId == null
                || userId == null
                || reservationId == null
                || occurredAt == null) {
            throw new IllegalArgumentException(
                    "OrderCancelledEvent is missing required fields"
            );
        }
    }
}
