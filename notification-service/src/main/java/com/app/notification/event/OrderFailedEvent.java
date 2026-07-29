package com.app.notification.event;

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
}
