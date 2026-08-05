package com.app.notification.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderConfirmedEvent(
        UUID messageId,
        int eventVersion,
        OrderEventType eventType,
        UUID orderId,
        UUID userId,
        Long reservationId,
        BigDecimal totalPrice,
        List<OrderItem> items,
        Instant occurredAt
) {

    /** Chạy cả khi Jackson deserialize, nên không tạo được instance sai. */
    public OrderConfirmedEvent {
        if (messageId == null
                || eventType != OrderEventType.ORDER_CONFIRMED
                || orderId == null
                || userId == null
                || reservationId == null
                || totalPrice == null
                || items == null
                || occurredAt == null) {
            throw new IllegalArgumentException(
                    "OrderConfirmedEvent is missing required fields"
            );
        }
    }
}
