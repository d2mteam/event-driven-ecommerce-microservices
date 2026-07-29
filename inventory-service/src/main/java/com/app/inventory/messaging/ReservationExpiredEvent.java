package com.app.inventory.messaging;

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

    public static ReservationExpiredEvent from(
            UUID messageId,
            UUID orderId,
            Long reservationId,
            Instant occurredAt
    ) {
        return new ReservationExpiredEvent(
                messageId,
                EventVersions.RESERVATION_EXPIRED,
                InventoryEventType.RESERVATION_EXPIRED,
                orderId,
                reservationId,
                occurredAt
        );
    }
}
