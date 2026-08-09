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

    Page<Product> findByStatusAndNameContainingIgnoreCase(
            ProductStatus status,
            String name,
            Pageable pageable
    );

    @Query("""
            select product
            from Product product
            where (:status is null or product.status = :status)
              and (:query is null or lower(product.name) like lower(concat('%', :query, '%')))
            """)
    Page<Product> findAdminProducts(
            @Param("status") ProductStatus status,
            @Param("query") String query,
            Pageable pageable
    );
}
