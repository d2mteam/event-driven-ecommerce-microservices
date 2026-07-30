package com.app.order.repository;

import com.app.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByUserIdAndIdempotencyKey(
            UUID userId,
            String idempotencyKey
    );
}
