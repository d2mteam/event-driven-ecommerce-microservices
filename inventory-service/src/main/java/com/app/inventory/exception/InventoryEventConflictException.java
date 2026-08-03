package com.app.inventory.exception;

public class InventoryEventConflictException extends RuntimeException {

    public InventoryEventConflictException(String message) {
        super(message);
    }

    public InventoryEventConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
