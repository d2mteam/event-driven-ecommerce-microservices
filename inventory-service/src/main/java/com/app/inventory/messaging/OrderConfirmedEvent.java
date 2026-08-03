package com.app.inventory.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderConfirmedEvent(
        UUID messageId,
        int eventVersion,
        OrderEventType eventType,
        UUID orderId,
        UUID userId,
        Long reservationId,
        BigDecimal totalPrice,
        List<OrderItem> items,
        Instant occurredAt
) {

    public void validate() {
        if (messageId == null
                || eventType != OrderEventType.ORDER_CONFIRMED
                || orderId == null
                || reservationId == null
                || occurredAt == null) {
            throw new IllegalArgumentException("OrderConfirmedEvent has missing fields");
        }
        if (eventVersion != EventVersions.ORDER_CONFIRMED) {
            throw new IllegalArgumentException(
                    "Unsupported OrderConfirmedEvent version: " + eventVersion
            );
        }
    }
}
