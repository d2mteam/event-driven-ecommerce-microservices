package com.app.order.repository;

import com.app.order.entity.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxMessageRepository
        extends JpaRepository<OutboxMessage, Long> {

    /**
     * Chỉ nhặt message khi mọi message cùng message_key trước nó đã PUBLISHED.
     * Nhờ vậy một lô claim về luôn có key đôi một khác nhau -- đó là lý do bắn
     * cả lô một lúc không làm sai thứ tự trong cùng một key.
     */
    @Query(
            value = """
                    select candidate.*
                    from order_outbox_messages candidate
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
                            from order_outbox_messages predecessor
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
    List<OutboxMessage> findClaimableForUpdate(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    @Modifying
    @Query("""
            update OutboxMessage message
            set message.status = com.app.order.model.OutboxStatus.PUBLISHED,
                message.publishedAt = :publishedAt,
                message.lockToken = null,
                message.lockedUntil = null,
                message.lastError = null
            where message.id in :ids
              and message.status = com.app.order.model.OutboxStatus.PROCESSING
              and message.lockToken = :lockToken
            """)
    int markPublishedAll(
            @Param("ids") List<Long> ids,
            @Param("lockToken") String lockToken,
            @Param("publishedAt") Instant publishedAt
    );

    /** attemptCount đã tăng lúc claim nên ở đây không đụng tới nữa. */
    @Modifying
    @Query("""
            update OutboxMessage message
            set message.status = com.app.order.model.OutboxStatus.PENDING,
                message.nextAttemptAt = :nextAttemptAt,
                message.lockToken = null,
                message.lockedUntil = null,
                message.lastError = :lastError
            where message.id in :ids
              and message.status = com.app.order.model.OutboxStatus.PROCESSING
              and message.lockToken = :lockToken
            """)
    int scheduleRetryAll(
            @Param("ids") List<Long> ids,
            @Param("lockToken") String lockToken,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("lastError") String lastError
    );

    /** Hết lượt thử. Dòng nằm lại đây chờ người xử lý -- xem requeue-outbox.sh. */
    @Modifying
    @Query("""
            update OutboxMessage message
            set message.status = com.app.order.model.OutboxStatus.FAILED,
                message.lockToken = null,
                message.lockedUntil = null,
                message.lastError = :lastError
            where message.id in :ids
              and message.status = com.app.order.model.OutboxStatus.PROCESSING
              and message.lockToken = :lockToken
            """)
    int markFailedAll(
            @Param("ids") List<Long> ids,
            @Param("lockToken") String lockToken,
            @Param("lastError") String lastError
    );
}
