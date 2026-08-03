package com.app.order.event;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record PaymentResultEvent(
        UUID messageId,
        int eventVersion,
        PaymentEventType eventType,
        Long paymentId,
        UUID orderId,
        BigDecimal amount,
        Instant occurredAt
) {
}
