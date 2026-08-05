package com.app.order.event;

import java.time.Instant;
import java.util.UUID;

public record ReservationExpiredEvent(
        UUID messageId,
        int eventVersion,
        InventoryEventType eventType,
        UUID orderId,
        Long reservationId,
        Instant occurredAt
) {

    /** Chạy cả khi Jackson deserialize, nên không tạo được instance sai. */
    public ReservationExpiredEvent {
        if (messageId == null
                || eventType == null
                || orderId == null
                || reservationId == null
                || occurredAt == null) {
            throw new IllegalArgumentException(
                    "ReservationExpiredEvent is missing required fields"
            );
        }
    }
}
