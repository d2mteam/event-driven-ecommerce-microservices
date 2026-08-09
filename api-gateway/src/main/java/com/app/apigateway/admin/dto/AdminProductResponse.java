package com.app.apigateway.admin.dto;

import java.math.BigDecimal;
import java.util.Map;

public record AdminProductResponse(
        Long id,
        Long sourceProductId,
        String name,
        BigDecimal price,
        String bulletPoints,
        String description,
        Long productTypeId,
        Double productLength,
        Map<String, String> attributes,
        String status
) {
}
