package com.app.productmanagement.service;

import com.app.productmanagement.mapper.ProductMapper;
import com.app.productmanagement.repository.ProductRepository;
import com.app.productmanagement.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductServiceImpl productService;

    @Test
    void searchesActiveProductsByNameAndCategory() {
        PageRequest pageable = PageRequest.of(0, 10);
        PageRequest searchPageable = PageRequest.of(
                0,
                10,
                JpaSort.unsafe(
                        Sort.Direction.DESC,
                        "match(product.name) against (:name in natural language mode)"
                ).and(Sort.by("id"))
        );
        when(productRepository.findProducts(
                "ACTIVE",
                "mouse",
                "Electronics",
                searchPageable
        )).thenReturn(Page.empty(searchPageable));

        productService.searchProducts(" mouse ", " Electronics ", pageable);

        verify(productRepository).findProducts(
                "ACTIVE",
                "mouse",
                "Electronics",
                searchPageable
        );
    }

    @Test
    void convertsBlankCustomerFiltersToNull() {
        PageRequest pageable = PageRequest.of(0, 10);
        PageRequest searchPageable = PageRequest.of(0, 10, Sort.by("id"));
        when(productRepository.findProducts(
                "ACTIVE",
                null,
                null,
                searchPageable
        )).thenReturn(Page.empty(searchPageable));

        productService.searchProducts(" \t\n ", "\u2003", pageable);

        verify(productRepository).findProducts(
                "ACTIVE",
                null,
                null,
                searchPageable
        );
    }

    @Test
    void convertsBlankAdminFiltersToNull() {
        PageRequest pageable = PageRequest.of(
                0,
                10,
                Sort.by(Sort.Direction.DESC, "price")
        );
        PageRequest stablePageable = PageRequest.of(
                0,
                10,
                pageable.getSort().and(Sort.by("id"))
        );
        when(productRepository.findProducts(
                null,
                null,
                null,
                stablePageable
        )).thenReturn(Page.empty(stablePageable));

        productService.getAdminProducts(null, " ", "", pageable);

        verify(productRepository).findProducts(
                null,
                null,
                null,
                stablePageable
        );
    }
}
