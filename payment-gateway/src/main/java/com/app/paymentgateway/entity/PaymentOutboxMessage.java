package com.app.paymentgateway.entity;

import com.app.paymentgateway.model.PaymentOutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "payment_outbox_messages",
        indexes = {
                @Index(
                        name = "idx_payment_outbox_claim",
                        columnList = "status, next_attempt_at, locked_until"
                ),
                @Index(
                        name = "idx_payment_outbox_key_order",
                        columnList = "message_key, id, status"
                )
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentOutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID messageId;

    @Column(nullable = false, updatable = false)
    private String topic;

    @Column(name = "message_key", nullable = false, updatable = false)
    private String key;

    @Column(nullable = false, updatable = false)
    private String type;

    @Lob
    @Column(nullable = false, updatable = false, columnDefinition = "longtext")
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lock_token", length = 36)
    private String lockToken;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "published_at")
    private Instant publishedAt;

    public void claim(String token, Instant leaseDeadline) {
        status = PaymentOutboxStatus.PROCESSING;
        attemptCount = Math.addExact(attemptCount, 1);
        lockToken = token;
        lockedUntil = leaseDeadline;
        lastError = null;
    }
}
