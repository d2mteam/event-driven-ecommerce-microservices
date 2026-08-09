package com.app.inventory.exception;

public enum InventoryErrorCode {
    PRODUCT_NOT_FOUND,
    INSUFFICIENT_STOCK,
    ADJUSTMENT_CONFLICTS_WITH_RESERVATIONS,
    IDEMPOTENCY_KEY_REUSED
}
