package com.app.apigateway.admin.dto;

public record InventorySummaryResponse(
        Long productId,
        Integer onHandQuantity,
        Integer reservedQuantity,
        Integer availableQuantity
) {
}
