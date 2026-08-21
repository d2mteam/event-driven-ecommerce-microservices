package com.app.productmanagement.repository;

import com.app.productmanagement.entity.Category;
import com.app.productmanagement.entity.Product;
import com.app.productmanagement.model.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = "category")
    Page<Product> findAllByStatus(ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findByIdAndStatus(Long id, ProductStatus status);

    @EntityGraph(attributePaths = "category")
    List<Product> findAllByIdInAndStatus(Collection<Long> ids, ProductStatus status);

    @Override
    @EntityGraph(attributePaths = "category")
    Optional<Product> findById(Long id);

    @Query(
            value = """
                    select product.*
                    from products product
                    where (:status is null or product.status = :status)
                      and (
                          :name is null
                          or match(product.name) against (:name in natural language mode)
                      )
                      and (:categoryId is null or product.category_id = :categoryId)
                    """,
            countQuery = """
                    select count(*)
                    from products product
                    where (:status is null or product.status = :status)
                      and (
                          :name is null
                          or match(product.name) against (:name in natural language mode)
                      )
                      and (:categoryId is null or product.category_id = :categoryId)
                    """,
            nativeQuery = true
    )
    Page<Product> findProducts(
            @Param("status") String status,
            @Param("name") String name,
            @Param("categoryId") Long categoryId,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            update Product product
            set product.category = :replacement
            where product.category.id = :categoryId
            """)
    int reassignCategory(
            @Param("categoryId") Long categoryId,
            @Param("replacement") Category replacement
    );
}
