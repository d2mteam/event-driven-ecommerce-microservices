package com.app.inventory.exception;

import java.util.List;

import lombok.Getter;

@Getter
public class InventoryConflictException extends RuntimeException {

    private final InventoryErrorCode code;
    private final List<Long> productIds;
    private final Long productId;
    private final Integer requestedQuantity;
    private final Integer availableQuantity;

    private InventoryConflictException(
            String message,
            InventoryErrorCode code,
            List<Long> productIds,
            Long productId,
            Integer requestedQuantity,
            Integer availableQuantity
    ) {
        super(message);
        this.code = code;
        this.productIds = productIds;
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public static InventoryConflictException productsNotFound(List<Long> productIds) {
        return new InventoryConflictException(
                "Inventory was not found for products: " + productIds,
                InventoryErrorCode.PRODUCT_NOT_FOUND,
                List.copyOf(productIds),
                null,
                null,
                null
        );
    }

    public static InventoryConflictException insufficientStock(
            Long productId,
            Integer requestedQuantity,
            Integer availableQuantity
    ) {
        return new InventoryConflictException(
                "Product %d has %d available, but %d was requested"
                        .formatted(productId, availableQuantity, requestedQuantity),
                InventoryErrorCode.INSUFFICIENT_STOCK,
                List.of(),
                productId,
                requestedQuantity,
                availableQuantity
        );
    }
}
