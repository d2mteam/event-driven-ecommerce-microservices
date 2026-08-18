package com.app.productmanagement.media.storage.repository;

import com.app.productmanagement.media.storage.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, String> {

    boolean existsByObjectKeyIn(Collection<String> objectKeys);

    List<ProductImage> findAllByProductId(Long productId);

    List<ProductImage> findAllByProductIdIn(Collection<Long> productIds);
}
