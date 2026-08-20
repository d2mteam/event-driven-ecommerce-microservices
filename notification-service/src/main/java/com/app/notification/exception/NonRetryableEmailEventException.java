package com.app.notification.exception;

public class NonRetryableEmailEventException extends RuntimeException {

    public NonRetryableEmailEventException(String message) {
        super(message);
    }

    public NonRetryableEmailEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
