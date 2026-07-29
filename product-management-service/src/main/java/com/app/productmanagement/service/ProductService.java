package com.app.productmanagement.service;

import com.app.productmanagement.dto.PageResponse;
import com.app.productmanagement.dto.ProductResponse;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public interface ProductService {

    PageResponse<ProductResponse> getProducts(Pageable pageable);

    ProductResponse getProduct(Long id);

    List<ProductResponse> getProductsByIds(Collection<Long> ids);

    PageResponse<ProductResponse> searchProducts(String name, Pageable pageable);
}
