package com.app.inventory.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.app.inventory.entity.ReservationItem;
import com.app.inventory.entity.ReservationStatus;

public record ReservationResponse(
        Long reservationId,
        UUID orderId,
        List<ReservationItem> items,
        ReservationStatus status,
        Instant expiresAt,
        Instant createdAt
) {
}
