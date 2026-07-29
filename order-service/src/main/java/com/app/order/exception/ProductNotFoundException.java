package com.app.order.exception;

import java.util.Collection;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Collection<Long> productIds) {
        super("Product not found: " + productIds);
    }
}
