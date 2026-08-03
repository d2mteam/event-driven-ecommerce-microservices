package com.app.paymentgateway.entity;

import com.app.paymentgateway.model.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payments_order_id",
                columnNames = "order_id"
        ),
        indexes = @Index(
                name = "idx_payments_pending_expiry",
                columnList = "status, expires_at"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private PaymentStatus status;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant completedAt;

    public static Payment pending(
            UUID orderId,
            BigDecimal amount,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new Payment(
                null,
                orderId,
                amount,
                PaymentStatus.PENDING,
                expiresAt,
                createdAt,
                null
        );
    }

    public boolean complete(PaymentStatus result, Instant completedAt) {
        if (status == result) {
            return false;
        }
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Payment is already " + status
            );
        }
        if (result != PaymentStatus.SUCCEEDED
                && result != PaymentStatus.FAILED) {
            throw new IllegalArgumentException(
                    "Payment can only complete as SUCCEEDED or FAILED"
            );
        }

        status = result;
        this.completedAt = completedAt;
        return true;
    }

    public boolean expire(Instant expiredAt) {
        if (status != PaymentStatus.PENDING) {
            return false;
        }

        status = PaymentStatus.EXPIRED;
        completedAt = expiredAt;
        return true;
    }
}
