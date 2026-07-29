package com.app.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventories")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long productId;

    @Column(name = "on_hand_quantity", nullable = false)
    private Integer onHandQuantity;

    @Column(nullable = false)
    private Integer reservedQuantity;

    public int availableQuantity() {
        return onHandQuantity - reservedQuantity;
    }

    public void reserve(int quantity) {
        requirePositive(quantity);
        if (availableQuantity() < quantity) {
            throw new IllegalStateException("Insufficient available inventory");
        }
        reservedQuantity += quantity;
    }

    public void settle(int quantity) {
        requireReserved(quantity);
        onHandQuantity -= quantity;
        reservedQuantity -= quantity;
    }

    public void release(int quantity) {
        requireReserved(quantity);
        reservedQuantity -= quantity;
    }

    private void requireReserved(int quantity) {
        requirePositive(quantity);
        if (reservedQuantity < quantity) {
            throw new IllegalStateException("Reserved inventory cannot become negative");
        }
    }

    private void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Inventory quantity must be greater than zero");
        }
    }
}
