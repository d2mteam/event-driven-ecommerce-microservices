package com.app.order.repository;

import com.app.order.entity.Order;
import com.app.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findAllByUserIdOrderByCreatedAtDesc(
            UUID userId,
            Pageable pageable
    );

    Page<Order> findAllByUserId(UUID userId, Pageable pageable);

    Page<Order> findAllByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findAllByUserIdAndStatus(
            UUID userId,
            OrderStatus status,
            Pageable pageable
    );

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id and o.userId = :userId")
    Optional<Order> findByIdAndUserIdForUpdate(
            @Param("id") UUID id,
            @Param("userId") UUID userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);
}
