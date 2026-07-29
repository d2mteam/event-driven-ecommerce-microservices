package com.app.inventory.entity;

public record ReservationItem(
        Long productId,
        Integer quantity
) {
}
