package com.app.productmanagement.dto;

public record CategoryResponse(
        Long id,
        String name,
        boolean active,
        boolean systemCategory
) {
}
