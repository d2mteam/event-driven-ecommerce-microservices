package com.app.productmanagement.mapper;

import com.app.productmanagement.dto.ProductResponse;
import com.app.productmanagement.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductMapper {

    ProductResponse toResponse(Product product);
}
