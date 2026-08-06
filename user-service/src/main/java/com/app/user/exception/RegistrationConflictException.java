package com.app.user.exception;

public class RegistrationConflictException extends RuntimeException {
    public RegistrationConflictException(String message) {
        super(message);
    }
}
