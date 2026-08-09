package com.app.productmanagement.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ProductStateConflictException extends RuntimeException {

    public ProductStateConflictException(String message) {
        super(message);
    }
}
