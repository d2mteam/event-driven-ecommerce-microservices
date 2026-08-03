package com.app.inventory.messaging;

public class NonRetryableOrderEventException extends RuntimeException {

    public NonRetryableOrderEventException(String message) {
        super(message);
    }

    public NonRetryableOrderEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
