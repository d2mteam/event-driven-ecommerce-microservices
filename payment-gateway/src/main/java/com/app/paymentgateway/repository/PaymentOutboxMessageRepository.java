package com.app.paymentgateway.repository;

import com.app.paymentgateway.entity.PaymentOutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PaymentOutboxMessageRepository
        extends JpaRepository<PaymentOutboxMessage, Long> {

    @Query(
            value = """
                    select candidate.*
                    from payment_outbox_messages candidate
                    where (
                            (
                                candidate.status = 'PENDING'
                                and candidate.next_attempt_at <= :now
                            )
                            or (
                                candidate.status = 'PROCESSING'
                                and candidate.locked_until <= :now
                            )
                        )
                      and not exists (
                            select 1
                            from payment_outbox_messages predecessor
                            where predecessor.message_key = candidate.message_key
                              and predecessor.id < candidate.id
                              and predecessor.status <> 'PUBLISHED'
                        )
                    order by candidate.id
                    limit :batchSize
                    for update skip locked
                    """,
            nativeQuery = true
    )
    List<PaymentOutboxMessage> findClaimableForUpdate(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    @Modifying
    @Query("""
            update PaymentOutboxMessage message
            set message.status = com.app.paymentgateway.model.PaymentOutboxStatus.PUBLISHED,
                message.publishedAt = :publishedAt,
                message.lockToken = null,
                message.lockedUntil = null,
                message.lastError = null
            where message.id in :ids
              and message.status = com.app.paymentgateway.model.PaymentOutboxStatus.PROCESSING
              and message.lockToken = :lockToken
            """)
    int markPublishedAll(
            @Param("ids") List<Long> ids,
            @Param("lockToken") String lockToken,
            @Param("publishedAt") Instant publishedAt
    );

    @Modifying
    @Query("""
            update PaymentOutboxMessage message
            set message.status = com.app.paymentgateway.model.PaymentOutboxStatus.PENDING,
                message.nextAttemptAt = :nextAttemptAt,
                message.lockToken = null,
                message.lockedUntil = null,
                message.lastError = :lastError
            where message.id in :ids
              and message.status = com.app.paymentgateway.model.PaymentOutboxStatus.PROCESSING
              and message.lockToken = :lockToken
            """)
    int scheduleRetryAll(
            @Param("ids") List<Long> ids,
            @Param("lockToken") String lockToken,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("lastError") String lastError
    );

    @Modifying
    @Query("""
            update PaymentOutboxMessage message
            set message.status = com.app.paymentgateway.model.PaymentOutboxStatus.FAILED,
                message.lockToken = null,
                message.lockedUntil = null,
                message.lastError = :lastError
            where message.id in :ids
              and message.status = com.app.paymentgateway.model.PaymentOutboxStatus.PROCESSING
              and message.lockToken = :lockToken
            """)
    int markFailedAll(
            @Param("ids") List<Long> ids,
            @Param("lockToken") String lockToken,
            @Param("lastError") String lastError
    );
}
