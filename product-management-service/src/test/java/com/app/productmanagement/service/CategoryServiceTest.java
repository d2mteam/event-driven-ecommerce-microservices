package com.app.productmanagement.service;

import com.app.productmanagement.dto.CategoryResponse;
import com.app.productmanagement.dto.CreateCategoryRequest;
import com.app.productmanagement.entity.Category;
import com.app.productmanagement.exception.CategoryStateConflictException;
import com.app.productmanagement.mapper.CategoryMapper;
import com.app.productmanagement.repository.CategoryRepository;
import com.app.productmanagement.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void createsTrimmedActiveCategory() {
        when(categoryRepository.findByNameIgnoreCase("Electronics"))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> {
                    Category category = invocation.getArgument(0);
                    category.setId(10L);
                    return category;
                });
        when(categoryMapper.toResponse(any(Category.class)))
                .thenAnswer(invocation -> {
                    Category category = invocation.getArgument(0);
                    return new CategoryResponse(
                            category.getId(),
                            category.getName(),
                            category.isActive(),
                            category.isSystemCategory()
                    );
                });

        var response = categoryService.create(
                new CreateCategoryRequest("  Electronics  ")
        );

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Electronics");
        assertThat(response.active()).isTrue();
        assertThat(response.systemCategory()).isFalse();
    }

    @Test
    void rejectsDuplicateNameIgnoringCase() {
        when(categoryRepository.findByNameIgnoreCase("electronics"))
                .thenReturn(Optional.of(Category.builder().id(1L).build()));

        assertThatThrownBy(() -> categoryService.create(
                new CreateCategoryRequest("electronics")
        )).isInstanceOf(CategoryStateConflictException.class);
    }

    @Test
    void archivesCategoryAfterMovingProductsToFallback() {
        Category category = Category.builder()
                .id(2L)
                .name("Old")
                .active(true)
                .build();
        Category fallback = Category.builder()
                .id(1L)
                .name("Khác")
                .active(true)
                .systemCategory(true)
                .build();
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(categoryRepository.findBySystemCategoryTrue())
                .thenReturn(Optional.of(fallback));

        categoryService.archive(2L);

        verify(productRepository).reassignCategory(2L, fallback);
        assertThat(category.isActive()).isFalse();
    }

    @Test
    void cannotArchiveSystemCategory() {
        Category fallback = Category.builder()
                .id(1L)
                .name("Khác")
                .active(true)
                .systemCategory(true)
                .build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(fallback));

        assertThatThrownBy(() -> categoryService.archive(1L))
                .isInstanceOf(CategoryStateConflictException.class);
        verify(productRepository, never()).reassignCategory(any(), any());
    }
}
