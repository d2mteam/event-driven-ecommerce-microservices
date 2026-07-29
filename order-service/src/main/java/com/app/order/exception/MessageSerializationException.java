package com.app.order.exception;

public class MessageSerializationException extends RuntimeException {

    public MessageSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
