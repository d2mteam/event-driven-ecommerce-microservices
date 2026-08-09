package com.app.paymentgateway.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderCancellationRequestedEvent(
        UUID messageId,
        int eventVersion,
        OrderEventType eventType,
        UUID orderId,
        Long reservationId,
        BigDecimal amount,
        Instant occurredAt
) {

    public OrderCancellationRequestedEvent {
        if (messageId == null
                || eventType != OrderEventType.ORDER_CANCELLATION_REQUESTED
                || orderId == null
                || reservationId == null
                || amount == null
                || occurredAt == null) {
            throw new IllegalArgumentException(
                    "OrderCancellationRequestedEvent is missing required fields"
            );
        }
    }
}
