package com.app.order.event;

import java.math.BigDecimal;

public record OrderConfirmedItem(
        Long productId,
        String productName,
        BigDecimal unitPrice,
        Integer quantity
) {
}
