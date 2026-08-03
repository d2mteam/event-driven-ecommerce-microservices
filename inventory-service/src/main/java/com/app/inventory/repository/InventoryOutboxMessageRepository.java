package com.app.inventory.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.app.inventory.entity.InventoryOutboxMessage;
import com.app.inventory.entity.InventoryOutboxStatus;

public interface InventoryOutboxMessageRepository
        extends JpaRepository<InventoryOutboxMessage, Long> {

    @Query(
            value = """
                    select *
                    from inventory_outbox_messages
                    where (
                            status = 'PENDING'
                            and next_attempt_at <= :now
                        )
                       or (
                            status = 'PROCESSING'
                            and locked_until <= :now
                        )
                    order by id
                    limit :batchSize
                    for update skip locked
                    """,
            nativeQuery = true
    )
    List<InventoryOutboxMessage> findClaimableForUpdate(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    @Modifying
    @Query("""
            update InventoryOutboxMessage message
            set message.status = :published,
                message.publishedAt = :publishedAt,
                message.lockToken = null,
                message.lockedUntil = null,
                message.lastError = null
            where message.id = :id
              and message.status = :processing
              and message.lockToken = :lockToken
            """)
    int markPublished(
            @Param("id") Long id,
            @Param("lockToken") String lockToken,
            @Param("processing") InventoryOutboxStatus processing,
            @Param("published") InventoryOutboxStatus published,
            @Param("publishedAt") Instant publishedAt
    );

    @Modifying
    @Query("""
            update InventoryOutboxMessage message
            set message.status = :pending,
                message.nextAttemptAt = :nextAttemptAt,
                message.lockToken = null,
                message.lockedUntil = null,
                message.lastError = :lastError
            where message.id = :id
              and message.status = :processing
              and message.lockToken = :lockToken
            """)
    int scheduleRetry(
            @Param("id") Long id,
            @Param("lockToken") String lockToken,
            @Param("processing") InventoryOutboxStatus processing,
            @Param("pending") InventoryOutboxStatus pending,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("lastError") String lastError
    );

    @Modifying
    @Query("""
            update InventoryOutboxMessage message
            set message.status = :failed,
                message.lockToken = null,
                message.lockedUntil = null,
                message.lastError = :lastError
            where message.id = :id
              and message.status = :processing
              and message.lockToken = :lockToken
            """)
    int markFailed(
            @Param("id") Long id,
            @Param("lockToken") String lockToken,
            @Param("processing") InventoryOutboxStatus processing,
            @Param("failed") InventoryOutboxStatus failed,
            @Param("lastError") String lastError
    );
}
