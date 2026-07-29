package com.app.order.client;

import java.math.BigDecimal;
import java.util.Map;

public record ProductClientResponse(
        Long id,
        String name,
        BigDecimal price,
        Map<String, String> attributes
) {
}
