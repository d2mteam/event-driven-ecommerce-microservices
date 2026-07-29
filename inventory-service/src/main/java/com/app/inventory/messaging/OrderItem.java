package com.app.inventory.messaging;

import java.math.BigDecimal;

public record OrderItem(
        Long productId,
        String productName,
        BigDecimal unitPrice,
        Integer quantity
) {
}
