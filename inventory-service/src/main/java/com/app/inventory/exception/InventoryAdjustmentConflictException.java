package com.app.inventory.exception;

import lombok.Getter;

@Getter
public class InventoryAdjustmentConflictException extends RuntimeException {

    private final InventoryErrorCode code;
    private final Long productId;
    private final Integer onHandQuantity;
    private final Integer reservedQuantity;
    private final Integer requestedDelta;
    private final Integer minimumAllowedDelta;

    private InventoryAdjustmentConflictException(
            String message,
            InventoryErrorCode code,
            Long productId,
            Integer onHandQuantity,
            Integer reservedQuantity,
            Integer requestedDelta,
            Integer minimumAllowedDelta
    ) {
        super(message);
        this.code = code;
        this.productId = productId;
        this.onHandQuantity = onHandQuantity;
        this.reservedQuantity = reservedQuantity;
        this.requestedDelta = requestedDelta;
        this.minimumAllowedDelta = minimumAllowedDelta;
    }

    public static InventoryAdjustmentConflictException affectsReservations(
            Long productId,
            int onHandQuantity,
            int reservedQuantity,
            int requestedDelta
    ) {
        return new InventoryAdjustmentConflictException(
                "Adjustment would reduce on-hand quantity below reserved quantity",
                InventoryErrorCode.ADJUSTMENT_CONFLICTS_WITH_RESERVATIONS,
                productId,
                onHandQuantity,
                reservedQuantity,
                requestedDelta,
                reservedQuantity - onHandQuantity
        );
    }

    public static InventoryAdjustmentConflictException idempotencyKeyReused(
            Long productId
    ) {
        return new InventoryAdjustmentConflictException(
                "Idempotency-Key was already used with another adjustment",
                InventoryErrorCode.IDEMPOTENCY_KEY_REUSED,
                productId,
                null,
                null,
                null,
                null
        );
    }
}
