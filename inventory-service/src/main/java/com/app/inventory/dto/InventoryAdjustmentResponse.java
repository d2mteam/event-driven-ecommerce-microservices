package com.app.inventory.dto;

import com.app.inventory.entity.InventoryAdjustmentReason;

import java.time.Instant;

public record InventoryAdjustmentResponse(
        Long adjustmentId,
        Long productId,
        Integer delta,
        InventoryAdjustmentReason reason,
        Integer previousOnHand,
        Integer resultingOnHand,
        Integer reservedQuantity,
        Integer availableQuantity,
        Instant createdAt
) {
}
