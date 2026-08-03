package com.app.paymentgateway.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResultEvent(
        UUID messageId,
        int eventVersion,
        PaymentEventType eventType,
        Long paymentId,
        UUID orderId,
        BigDecimal amount,
        Instant occurredAt
) implements OutboxPayload {
}
