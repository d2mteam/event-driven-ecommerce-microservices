package com.app.inventory.dto;

import com.app.inventory.entity.InventoryAdjustmentReason;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record InventoryAdjustmentRequest(
        @NotNull Integer delta,
        @NotNull InventoryAdjustmentReason reason
) {

    @AssertTrue(message = "delta does not match the adjustment reason")
    public boolean isDeltaValid() {
        if (delta == null || reason == null) {
            return true;
        }
        return switch (reason) {
            case RESTOCK -> delta > 0;
            case DAMAGE -> delta < 0;
            case CORRECTION -> delta != 0;
        };
    }
}
