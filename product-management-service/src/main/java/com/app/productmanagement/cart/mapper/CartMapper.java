package com.app.productmanagement.cart.mapper;

import com.app.productmanagement.cart.dto.CartItemResponse;
import com.app.productmanagement.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CartMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "unitPrice", source = "product.price")
    CartItemResponse toResponse(Product product, int quantity);
}
