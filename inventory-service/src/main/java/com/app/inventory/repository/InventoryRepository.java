package com.app.inventory.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.app.inventory.entity.Inventory;

import jakarta.persistence.LockModeType;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Modifying
    @Query(value = """
            insert into inventories (product_id, on_hand_quantity, reserved_quantity)
            values (:productId, 0, 0)
            on duplicate key update product_id = product_id
            """, nativeQuery = true)
    int ensureExists(@Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Inventory> findByProductId(Long productId);

    List<Inventory> findAllByProductIdIn(Collection<Long> productIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select inventory
            from Inventory inventory
            where inventory.productId in :productIds
            order by inventory.productId
            """)
    List<Inventory> findAllByProductIdForUpdate(@Param("productIds") Collection<Long> productIds);
}
