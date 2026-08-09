package com.app.inventory.dto;

public record InventorySummaryResponse(
        Long productId,
        Integer onHandQuantity,
        Integer reservedQuantity,
        Integer availableQuantity
) {
}
