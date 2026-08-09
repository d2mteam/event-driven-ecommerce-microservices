package com.app.productmanagement.mapper;

import com.app.productmanagement.dto.CreateProductRequest;
import com.app.productmanagement.dto.ProductResponse;
import com.app.productmanagement.dto.UpdateProductRequest;
import com.app.productmanagement.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductMapper {

    ProductResponse toResponse(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    Product toEntity(CreateProductRequest request);

    @Mapping(target = "id", ignore = true)
    void update(UpdateProductRequest request, @MappingTarget Product product);
}
