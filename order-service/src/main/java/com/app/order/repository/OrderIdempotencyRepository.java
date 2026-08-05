package com.app.order.repository;

import com.app.order.entity.OrderIdempotency;
import com.app.order.model.IdempotencyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderIdempotencyRepository
        extends JpaRepository<OrderIdempotency, Long> {

    Optional<OrderIdempotency> findByUserIdAndIdempotencyKey(
            UUID userId,
            String idempotencyKey
    );

    @Modifying
    @Query("""
            update OrderIdempotency record
            set record.status = :newStatus
            where record.id = :id
              and record.status = :currentStatus
            """)
    int changeStatus(
            @Param("id") Long id,
            @Param("currentStatus") IdempotencyStatus currentStatus,
            @Param("newStatus") IdempotencyStatus newStatus
    );

    @Modifying
    @Query("""
            update OrderIdempotency record
            set record.status = :newStatus,
                record.orderId = :orderId
            where record.id = :id
              and record.status = :currentStatus
            """)
    int complete(
            @Param("id") Long id,
            @Param("currentStatus") IdempotencyStatus currentStatus,
            @Param("newStatus") IdempotencyStatus newStatus,
            @Param("orderId") UUID orderId
    );
}
