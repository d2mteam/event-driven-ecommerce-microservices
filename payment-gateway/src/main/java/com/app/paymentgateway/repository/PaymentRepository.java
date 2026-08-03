package com.app.paymentgateway.repository;

import com.app.paymentgateway.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select *
            from payments
            where status = :status
              and expires_at <= :expiresAt
            order by expires_at, id
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<Payment> findExpiredForUpdate(
            @Param("status") String status,
            @Param("expiresAt") Instant expiresAt,
            @Param("batchSize") int batchSize
    );
}
