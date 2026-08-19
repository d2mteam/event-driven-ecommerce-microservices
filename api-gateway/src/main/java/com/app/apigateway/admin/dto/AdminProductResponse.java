package com.app.apigateway.admin.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AdminProductResponse(
        Long id,
        String name,
        Long categoryId,
        String category,
        BigDecimal price,
        String description,
        Map<String, String> attributes,
        String status,
        List<String> imageUrls
) {
}
