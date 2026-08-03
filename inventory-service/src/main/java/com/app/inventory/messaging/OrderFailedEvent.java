package com.app.inventory.messaging;

import java.time.Instant;
import java.util.UUID;

public record OrderFailedEvent(
        UUID messageId,
        int eventVersion,
        OrderEventType eventType,
        UUID orderId,
        UUID userId,
        Long reservationId,
        String reason,
        Instant occurredAt
) {

    public void validate() {
        if (messageId == null
                || eventType != OrderEventType.ORDER_FAILED
                || orderId == null
                || reservationId == null
                || occurredAt == null) {
            throw new IllegalArgumentException("OrderFailedEvent has missing fields");
        }
        if (eventVersion != EventVersions.ORDER_FAILED) {
            throw new IllegalArgumentException(
                    "Unsupported OrderFailedEvent version: " + eventVersion
            );
        }
    }
}
