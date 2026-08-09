package com.app.inventory.repository;

import com.app.inventory.entity.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryAdjustmentRepository
        extends JpaRepository<InventoryAdjustment, Long> {

    Optional<InventoryAdjustment> findByProductIdAndIdempotencyKey(
            Long productId,
            String idempotencyKey
    );
}
