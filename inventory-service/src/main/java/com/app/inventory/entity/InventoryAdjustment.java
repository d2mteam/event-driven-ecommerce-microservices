package com.app.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_adjustments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_adjustment_product_key",
                columnNames = {"product_id", "idempotency_key"}
        )
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false)
    private Integer delta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryAdjustmentReason reason;

    @Column(name = "previous_on_hand", nullable = false)
    private Integer previousOnHand;

    @Column(name = "resulting_on_hand", nullable = false)
    private Integer resultingOnHand;

    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity;

    @Column(name = "resulting_available", nullable = false)
    private Integer resultingAvailable;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
