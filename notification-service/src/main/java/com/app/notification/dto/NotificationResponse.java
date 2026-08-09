package com.app.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        Long id,
        UUID orderId,
        String message,
        Instant createdAt
) {
}
