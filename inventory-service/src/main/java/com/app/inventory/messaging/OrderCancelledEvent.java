package com.app.inventory.messaging;

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
) {

    public OrderCancelledEvent {
        if (messageId == null
                || eventType != OrderEventType.ORDER_CANCELLED
                || orderId == null
                || reservationId == null
                || occurredAt == null) {
            throw new IllegalArgumentException(
                    "OrderCancelledEvent is missing required fields"
            );
        }
    }
}
