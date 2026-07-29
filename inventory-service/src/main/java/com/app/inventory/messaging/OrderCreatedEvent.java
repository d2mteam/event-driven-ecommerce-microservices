package com.app.inventory.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
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
                || eventType != OrderEventType.ORDER_CREATED
                || orderId == null
                || reservationId == null
                || occurredAt == null) {
            throw new IllegalArgumentException("OrderCreatedEvent has missing fields");
        }
        if (eventVersion != EventVersions.ORDER_CREATED) {
            throw new IllegalArgumentException("Unsupported OrderCreatedEvent version: " + eventVersion);
        }
    }
}
