package com.app.order.repository;

import com.app.order.entity.OrderIdempotency;
import com.app.order.model.IdempotencyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderIdempotencyRepository
        extends JpaRepository<OrderIdempotency, Long> {

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
}
