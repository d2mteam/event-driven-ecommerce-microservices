package com.app.notification.event;

import java.math.BigDecimal;

public record OrderItem(
        Long productId,
        String productName,
        BigDecimal unitPrice,
        Integer quantity
) {
}
