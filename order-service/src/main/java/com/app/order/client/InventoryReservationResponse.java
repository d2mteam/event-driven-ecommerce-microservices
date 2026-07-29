package com.app.order.client;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservationResponse(
        Long reservationId,
        UUID orderId,
        ReservationStatus status,
        Instant expiresAt
) {
}
