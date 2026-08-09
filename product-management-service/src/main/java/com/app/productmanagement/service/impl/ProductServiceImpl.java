package com.app.productmanagement.service.impl;

import com.app.productmanagement.config.ProductCacheNames;
import com.app.productmanagement.dto.CreateProductRequest;
import com.app.productmanagement.dto.PageResponse;
import com.app.productmanagement.dto.ProductResponse;
import com.app.productmanagement.dto.UpdateProductRequest;
import com.app.productmanagement.entity.Product;
import com.app.productmanagement.exception.ProductNotFoundException;
import com.app.productmanagement.exception.ProductStateConflictException;
import com.app.productmanagement.mapper.ProductMapper;
import com.app.productmanagement.model.ProductStatus;
import com.app.productmanagement.repository.ProductRepository;
import com.app.productmanagement.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> ADMIN_SORT_FIELDS = Set.of(
            "id",
            "name",
            "price",
            "status"
    );

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    @Cacheable(
            cacheNames = ProductCacheNames.PRODUCT_PAGE,
            key = ProductCacheNames.PRODUCT_PAGE_KEY
    )
    public PageResponse<ProductResponse> getProducts(Pageable pageable) {
        Page<ProductResponse> products = productRepository
                .findAllByStatus(ProductStatus.ACTIVE, pageable)
                .map(productMapper::toResponse);
        return PageResponse.from(products);
    }

    @Override
    @Cacheable(
            cacheNames = ProductCacheNames.PRODUCT_DETAIL,
            key = "#id"
    )
    public ProductResponse getProduct(Long id) {
        Product product = productRepository
                .findByIdAndStatus(id, ProductStatus.ACTIVE)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return productMapper.toResponse(product);
    }

    @Override
    public List<ProductResponse> getProductsByIds(Collection<Long> ids) {
        return productRepository
                .findAllByIdInAndStatus(ids, ProductStatus.ACTIVE)
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @Override
    public PageResponse<ProductResponse> searchProducts(
            String name,
            String category,
            Pageable pageable
    ) {
        String normalizedName = name == null || name.isBlank() ? null : name.trim();
        String normalizedCategory = category == null || category.isBlank()
                ? null
                : category.trim();
        Page<ProductResponse> products = productRepository
                .searchActiveProducts(
                        normalizedName,
                        normalizedCategory,
                        pageable
                )
                .map(productMapper::toResponse);
        return PageResponse.from(products);
    }

    @Override
    public PageResponse<ProductResponse> getAdminProducts(
            ProductStatus status,
            String query,
            Pageable pageable
    ) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must not exceed " + MAX_PAGE_SIZE
            );
        }
        boolean invalidSort = pageable.getSort().stream()
                .map(Sort.Order::getProperty)
                .anyMatch(property -> !ADMIN_SORT_FIELDS.contains(property));
        if (invalidSort) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Admin catalog can only sort by id, name, price or status"
            );
        }

        Sort stableSort = pageable.getSort().isUnsorted()
                ? Sort.by("id")
                : pageable.getSort();
        boolean hasIdSort = stableSort.stream()
                .anyMatch(order -> order.getProperty().equals("id"));
        if (!hasIdSort) {
            stableSort = stableSort.and(Sort.by("id"));
        }
        Pageable stablePageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                stableSort
        );

        String normalizedQuery = query == null || query.isBlank()
                ? null
                : query.trim();
        Page<ProductResponse> products = productRepository
                .findAdminProducts(
                        status == null ? null : status.name(),
                        normalizedQuery,
                        stablePageable
                )
                .map(productMapper::toResponse);
        return PageResponse.from(products);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = ProductCacheNames.PRODUCT_PAGE, allEntries = true)
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = productMapper.toEntity(request);
        product.setStatus(ProductStatus.DRAFT);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = ProductCacheNames.PRODUCT_DETAIL, key = "#id"),
            @CacheEvict(cacheNames = ProductCacheNames.PRODUCT_PAGE, allEntries = true)
    })
    public ProductResponse updateProduct(
            Long id,
            UpdateProductRequest request
    ) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw new ProductStateConflictException(
                    "Archived product cannot be updated: " + id
            );
        }
        if (request.status() == ProductStatus.ARCHIVED) {
            throw new ProductStateConflictException(
                    "Use DELETE to archive product: " + id
            );
        }

        productMapper.update(request, product);
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = ProductCacheNames.PRODUCT_DETAIL, key = "#id"),
            @CacheEvict(cacheNames = ProductCacheNames.PRODUCT_PAGE, allEntries = true)
    })
    public void archiveProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.setStatus(ProductStatus.ARCHIVED);
    }
}
