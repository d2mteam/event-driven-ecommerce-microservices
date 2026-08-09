package com.app.productmanagement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record CreateProductRequest(
        @NotBlank @Size(max = 500) String name,
        @Size(max = 100) String category,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        String bulletPoints,
        String description,
        Long productTypeId,
        Double productLength,
        Map<String, String> attributes
) {
}
