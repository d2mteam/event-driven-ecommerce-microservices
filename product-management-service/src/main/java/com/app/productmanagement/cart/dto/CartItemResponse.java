package com.app.productmanagement.cart.dto;

import java.math.BigDecimal;

public record CartItemResponse(
        Long productId,
        String name,
        String category,
        BigDecimal unitPrice,
        int quantity
) {
}
