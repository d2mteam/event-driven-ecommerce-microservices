package com.app.productmanagement.repository;

import com.app.productmanagement.entity.Product;
import com.app.productmanagement.model.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findAllByStatus(ProductStatus status, Pageable pageable);

    Optional<Product> findByIdAndStatus(Long id, ProductStatus status);

    List<Product> findAllByIdInAndStatus(Collection<Long> ids, ProductStatus status);

    @Query(
            value = """
                    select product.*
                    from products product
                    where product.status = 'ACTIVE'
                      and (
                          :name is null
                          or match(product.name) against (:name in natural language mode)
                      )
                      and (:category is null or product.category = :category)
                    """,
            countQuery = """
                    select count(*)
                    from products product
                    where product.status = 'ACTIVE'
                      and (
                          :name is null
                          or match(product.name) against (:name in natural language mode)
                      )
                      and (:category is null or product.category = :category)
                    """,
            nativeQuery = true
    )
    Page<Product> searchActiveProducts(
            @Param("name") String name,
            @Param("category") String category,
            Pageable pageable
    );

    @Query(
            value = """
                    select product.*
                    from products product
                    where (:status is null or product.status = :status)
                      and (
                          :query is null
                          or match(product.name) against (:query in natural language mode)
                          or product.category = :query
                      )
                    """,
            countQuery = """
                    select count(*)
                    from products product
                    where (:status is null or product.status = :status)
                      and (
                          :query is null
                          or match(product.name) against (:query in natural language mode)
                          or product.category = :query
                      )
                    """,
            nativeQuery = true
    )
    Page<Product> findAdminProducts(
            @Param("status") String status,
            @Param("query") String query,
            Pageable pageable
    );
}
