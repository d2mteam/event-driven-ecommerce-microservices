package com.app.apigateway.admin.dto;

public record AdminCatalogItemResponse(
        AdminProductResponse product,
        InventorySummaryResponse inventory,
        String inventoryState
) {
}
