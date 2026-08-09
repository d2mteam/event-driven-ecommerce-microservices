package com.app.apigateway.admin;

import com.app.apigateway.admin.dto.AdminCatalogItemResponse;
import com.app.apigateway.admin.dto.AdminProductResponse;
import com.app.apigateway.admin.dto.InventorySummaryResponse;
import com.app.apigateway.admin.dto.PageResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AdminCatalogMapper {

    @Mapping(
            target = "inventoryState",
            expression = "java(inventory == null ? \"NOT_INITIALIZED\" : \"READY\")"
    )
    AdminCatalogItemResponse toItem(
            AdminProductResponse product,
            InventorySummaryResponse inventory
    );

    @Mapping(target = "content", source = "items")
    @Mapping(target = "page", source = "productPage.page")
    @Mapping(target = "size", source = "productPage.size")
    @Mapping(target = "totalElements", source = "productPage.totalElements")
    @Mapping(target = "totalPages", source = "productPage.totalPages")
    @Mapping(target = "last", source = "productPage.last")
    PageResponse<AdminCatalogItemResponse> toPage(
            PageResponse<AdminProductResponse> productPage,
            List<AdminCatalogItemResponse> items
    );
}
