package com.app.productmanagement.service;

import com.app.productmanagement.dto.CreateProductRequest;
import com.app.productmanagement.dto.PageResponse;
import com.app.productmanagement.dto.ProductResponse;
import com.app.productmanagement.dto.UpdateProductRequest;
import com.app.productmanagement.model.ProductStatus;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public interface ProductService {

    PageResponse<ProductResponse> getProducts(Pageable pageable);

    ProductResponse getProduct(Long id);

    List<ProductResponse> getProductsByIds(Collection<Long> ids);

    PageResponse<ProductResponse> searchProducts(
            String name,
            String category,
            Pageable pageable
    );

    PageResponse<ProductResponse> getAdminProducts(
            ProductStatus status,
            String name,
            String category,
            Pageable pageable
    );

    ProductResponse createProduct(CreateProductRequest request);

    ProductResponse updateProduct(Long id, UpdateProductRequest request);

    void archiveProduct(Long id);
}
