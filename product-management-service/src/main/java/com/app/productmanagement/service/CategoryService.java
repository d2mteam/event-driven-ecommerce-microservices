package com.app.productmanagement.service;

import com.app.productmanagement.config.ProductCacheNames;
import com.app.productmanagement.dto.CategoryResponse;
import com.app.productmanagement.dto.CreateCategoryRequest;
import com.app.productmanagement.dto.UpdateCategoryRequest;
import com.app.productmanagement.entity.Category;
import com.app.productmanagement.exception.CategoryNotFoundException;
import com.app.productmanagement.exception.CategoryStateConflictException;
import com.app.productmanagement.mapper.CategoryMapper;
import com.app.productmanagement.repository.CategoryRepository;
import com.app.productmanagement.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    public List<CategoryResponse> getActive() {
        return categoryRepository.findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    public List<CategoryResponse> getAll() {
        return categoryRepository.findAllByOrderByNameAsc()
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String name = request.name().strip();
        ensureUniqueName(name, null);
        Category category = Category.builder()
                .name(name)
                .active(true)
                .build();
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = ProductCacheNames.PRODUCT_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = ProductCacheNames.PRODUCT_PAGE, allEntries = true)
    })
    public CategoryResponse update(Long id, UpdateCategoryRequest request) {
        Category category = getCategory(id);
        rejectSystemCategory(category);
        if (category.isActive() && !request.active()) {
            throw new CategoryStateConflictException(
                    "Use DELETE to archive category: " + id
            );
        }

        String name = request.name().strip();
        ensureUniqueName(name, id);
        category.setName(name);
        category.setActive(request.active());
        return categoryMapper.toResponse(category);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = ProductCacheNames.PRODUCT_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = ProductCacheNames.PRODUCT_PAGE, allEntries = true)
    })
    public void archive(Long id) {
        Category category = getCategory(id);
        rejectSystemCategory(category);
        Category fallback = categoryRepository.findBySystemCategoryTrue()
                .orElseThrow(() -> new CategoryStateConflictException(
                        "System category is missing"
                ));

        productRepository.reassignCategory(id, fallback);
        category.setActive(false);
    }

    private Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }

    private void ensureUniqueName(String name, Long currentId) {
        categoryRepository.findByNameIgnoreCase(name)
                .filter(category -> !category.getId().equals(currentId))
                .ifPresent(category -> {
                    throw new CategoryStateConflictException(
                            "Category name already exists: " + name
                    );
                });
    }

    private static void rejectSystemCategory(Category category) {
        if (category.isSystemCategory()) {
            throw new CategoryStateConflictException(
                    "System category cannot be changed"
            );
        }
    }
}
