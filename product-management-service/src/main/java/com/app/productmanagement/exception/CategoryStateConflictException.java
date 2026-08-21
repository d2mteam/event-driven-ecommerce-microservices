package com.app.productmanagement.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CategoryStateConflictException extends RuntimeException {

    public CategoryStateConflictException(String message) {
        super(message);
    }
}
