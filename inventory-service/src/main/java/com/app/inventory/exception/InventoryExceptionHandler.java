package com.app.inventory.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InventoryExceptionHandler {

    private static final URI INVENTORY_CONFLICT_TYPE = URI.create("urn:problem:inventory-conflict");

    @ExceptionHandler(InventoryConflictException.class)
    public ResponseEntity<ProblemDetail> handleInventoryConflict(InventoryConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
        problem.setType(INVENTORY_CONFLICT_TYPE);
        problem.setTitle("Inventory reservation failed");
        problem.setProperty("code", exception.getCode());

        if (!exception.getProductIds().isEmpty()) {
            problem.setProperty("productIds", exception.getProductIds());
        }
        if (exception.getProductId() != null) {
            problem.setProperty("productId", exception.getProductId());
            problem.setProperty("requestedQuantity", exception.getRequestedQuantity());
            problem.setProperty("availableQuantity", exception.getAvailableQuantity());
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
