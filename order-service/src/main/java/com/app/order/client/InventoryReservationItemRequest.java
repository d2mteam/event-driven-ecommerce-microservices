package com.app.order.client;

public record InventoryReservationItemRequest(
        Long productId,
        Integer quantity
) {
}
