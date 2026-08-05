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

    /** Chạy cả khi Jackson deserialize, nên không tạo được instance sai. */
    public PaymentResultEvent {
        if (messageId == null
                || eventType == null
                || paymentId == null
                || orderId == null
                || amount == null
                || occurredAt == null) {
            throw new IllegalArgumentException(
                    "PaymentResultEvent is missing required fields"
            );
        }
    }
}
