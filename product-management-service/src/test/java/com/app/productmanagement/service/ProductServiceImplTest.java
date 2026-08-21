package com.app.productmanagement.service;

import com.app.productmanagement.dto.CreateProductRequest;
import com.app.productmanagement.entity.Category;
import com.app.productmanagement.entity.Product;
import com.app.productmanagement.exception.CategoryStateConflictException;
import com.app.productmanagement.mapper.ProductMapper;
import com.app.productmanagement.repository.CategoryRepository;
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

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductServiceImpl productService;

    @Test
    void searchesActiveProductsByNameAndCategoryId() {
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
                3L,
                searchPageable
        )).thenReturn(Page.empty(searchPageable));

        productService.searchProducts(" mouse ", 3L, pageable);

        verify(productRepository).findProducts(
                "ACTIVE",
                "mouse",
                3L,
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

        productService.searchProducts(" \t\n ", null, pageable);

        verify(productRepository).findProducts(
                "ACTIVE",
                null,
                null,
                searchPageable
        );
    }

    @Test
    void preservesCustomerSortForCategoryOnlySearch() {
        PageRequest pageable = PageRequest.of(
                0,
                10,
                Sort.by(Sort.Direction.ASC, "price")
        );
        PageRequest searchPageable = PageRequest.of(
                0,
                10,
                pageable.getSort().and(Sort.by("id"))
        );
        when(productRepository.findProducts(
                "ACTIVE",
                null,
                3L,
                searchPageable
        )).thenReturn(Page.empty(searchPageable));

        productService.searchProducts(null, 3L, pageable);

        verify(productRepository).findProducts(
                "ACTIVE",
                null,
                3L,
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

        productService.getAdminProducts(null, " ", null, pageable);

        verify(productRepository).findProducts(
                null,
                null,
                null,
                stablePageable
        );
    }

    @Test
    void assignsAnActiveCategoryWhenCreatingProduct() {
        CreateProductRequest request = new CreateProductRequest(
                "Mouse",
                3L,
                BigDecimal.TEN,
                "Wireless mouse",
                Map.of()
        );
        Category category = Category.builder()
                .id(3L)
                .name("Accessory")
                .active(true)
                .build();
        Product product = Product.builder().build();

        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(productMapper.toEntity(request)).thenReturn(product);
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        productService.createProduct(request);

        verify(productRepository).save(product);
        assertThat(product.getCategory()).isSameAs(category);
    }

    @Test
    void rejectsAnInactiveCategoryWhenCreatingProduct() {
        CreateProductRequest request = new CreateProductRequest(
                "Mouse",
                3L,
                BigDecimal.TEN,
                null,
                Map.of()
        );
        Category category = Category.builder()
                .id(3L)
                .name("Accessory")
                .active(false)
                .build();
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(CategoryStateConflictException.class);
    }
}
