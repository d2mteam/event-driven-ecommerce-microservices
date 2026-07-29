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
}
