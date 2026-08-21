package com.app.productmanagement.dto;

import com.app.productmanagement.model.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record UpdateProductRequest(
        @NotBlank @Size(max = 500) String name,
        @NotNull Long categoryId,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        String description,
        Map<String, String> attributes,
        @NotNull ProductStatus status,
        @Size(max = 8) List<String> imageKeys
) {
}
