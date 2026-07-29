package com.app.productmanagement.controller;

import com.app.productmanagement.dto.PageResponse;
import com.app.productmanagement.dto.ProductResponse;
import com.app.productmanagement.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public PageResponse<ProductResponse> getProducts(
            @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        return productService.getProducts(pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable Long id) {
        return productService.getProduct(id);
    }

    @PostMapping("/batch")
    public List<ProductResponse> getProductsByIds(@RequestBody Set<Long> ids) {
        return productService.getProductsByIds(ids);
    }

    @GetMapping("/search")
    public PageResponse<ProductResponse> searchProducts(
            @RequestParam String name,
            @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        return productService.searchProducts(name, pageable);
    }
}
