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

    /** Chạy cả khi Jackson deserialize, nên không tạo được instance sai. */
    public OrderFailedEvent {
        if (messageId == null
                || eventType != OrderEventType.ORDER_FAILED
                || orderId == null
                || reservationId == null
                || occurredAt == null) {
            throw new IllegalArgumentException(
                    "OrderFailedEvent is missing required fields"
            );
        }
    }

}
