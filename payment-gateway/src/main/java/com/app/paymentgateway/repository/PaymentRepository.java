package com.app.paymentgateway.repository;

import com.app.paymentgateway.entity.Payment;
import com.app.paymentgateway.model.PaymentStatus;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select payment
            from Payment payment
            where payment.status = :status
              and payment.expiresAt <= :expiresAt
            order by payment.id
            """)
    List<Payment> findExpiredForUpdate(
            @Param("status") PaymentStatus status,
            @Param("expiresAt") Instant expiresAt
    );
}
