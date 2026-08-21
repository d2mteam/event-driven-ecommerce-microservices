package com.app.productmanagement.mapper;

import com.app.productmanagement.dto.CategoryResponse;
import com.app.productmanagement.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);
}
