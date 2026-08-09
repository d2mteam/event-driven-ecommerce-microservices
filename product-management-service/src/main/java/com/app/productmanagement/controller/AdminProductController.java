package com.app.productmanagement.controller;

import com.app.productmanagement.dto.CreateProductRequest;
import com.app.productmanagement.dto.PageResponse;
import com.app.productmanagement.dto.ProductResponse;
import com.app.productmanagement.dto.UpdateProductRequest;
import com.app.productmanagement.model.ProductStatus;
import com.app.productmanagement.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class AdminProductController {

    private static final String ADMIN_PRODUCTS_PATH = "/api/admin/products";

    private final ProductService productService;

    @GetMapping("/internal/admin/products")
    public PageResponse<ProductResponse> getProducts(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        return productService.getAdminProducts(status, query, pageable);
    }

    @PostMapping(ADMIN_PRODUCTS_PATH)
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        ProductResponse product = productService.createProduct(request);
        return ResponseEntity
                .created(URI.create(ADMIN_PRODUCTS_PATH + "/" + product.getId()))
                .body(product);
    }

    @PutMapping(ADMIN_PRODUCTS_PATH + "/{id}")
    public ProductResponse updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return productService.updateProduct(id, request);
    }

    @DeleteMapping(ADMIN_PRODUCTS_PATH + "/{id}")
    public ResponseEntity<Void> archiveProduct(@PathVariable Long id) {
        productService.archiveProduct(id);
        return ResponseEntity.noContent().build();
    }
}
