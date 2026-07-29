package com.app.order.client;

import java.util.List;
import java.util.UUID;

public record InventoryReservationRequest(
        UUID orderId,
        List<InventoryReservationItemRequest> items
) {
}
