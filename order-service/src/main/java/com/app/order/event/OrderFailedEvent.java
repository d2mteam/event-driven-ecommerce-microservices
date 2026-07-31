package com.app.order.event;

import java.time.Instant;
import java.util.UUID;

import com.app.order.model.OrderFailureReason;
import lombok.Builder;

@Builder
public record OrderFailedEvent(
        UUID messageId,
        int eventVersion,
        OrderEventType eventType,
        UUID orderId,
        UUID userId,
        Long reservationId,
        OrderFailureReason reason,
        Instant occurredAt
) implements OutboxPayload {
}
