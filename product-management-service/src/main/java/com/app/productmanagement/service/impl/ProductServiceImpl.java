package com.app.productmanagement.service.impl;

import com.app.productmanagement.config.ProductCacheNames;
import com.app.productmanagement.dto.PageResponse;
import com.app.productmanagement.dto.ProductResponse;
import com.app.productmanagement.entity.Product;
import com.app.productmanagement.exception.ProductNotFoundException;
import com.app.productmanagement.mapper.ProductMapper;
import com.app.productmanagement.repository.ProductRepository;
import com.app.productmanagement.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    @Cacheable(
            cacheNames = ProductCacheNames.PRODUCT_PAGE,
            key = ProductCacheNames.PRODUCT_PAGE_KEY
    )
    public PageResponse<ProductResponse> getProducts(Pageable pageable) {
        Page<ProductResponse> products = productRepository.findAll(pageable)
                .map(productMapper::toResponse);
        return PageResponse.from(products);
    }

    @Override
    @Cacheable(
            cacheNames = ProductCacheNames.PRODUCT_DETAIL,
            key = "#id"
    )
    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return productMapper.toResponse(product);
    }

    @Override
    public List<ProductResponse> getProductsByIds(Collection<Long> ids) {
        return productRepository.findAllById(ids).stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @Override
    public PageResponse<ProductResponse> searchProducts(String name, Pageable pageable) {
        Page<ProductResponse> products = productRepository
                .findByNameContainingIgnoreCase(name, pageable)
                .map(productMapper::toResponse);
        return PageResponse.from(products);
    }
}
