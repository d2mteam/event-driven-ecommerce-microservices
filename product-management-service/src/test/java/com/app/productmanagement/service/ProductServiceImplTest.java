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
        when(productRepository.searchActiveProducts(
                "mouse",
                "Electronics",
                pageable
        )).thenReturn(Page.empty(pageable));

        productService.searchProducts(" mouse ", " Electronics ", pageable);

        verify(productRepository).searchActiveProducts(
                "mouse",
                "Electronics",
                pageable
        );
    }
}
