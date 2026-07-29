package com.app.order.exception;

public class InventoryConflictException extends RuntimeException {

    public InventoryConflictException() {
        super("One or more products are out of stock");
    }
}
