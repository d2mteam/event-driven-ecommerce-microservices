package com.app.notification.repository;

import com.app.notification.entity.Notification;
import com.app.notification.model.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByOrderIdIn(Collection<UUID> orderIds);

    Page<Notification> findAllByUserIdOrderByCreatedAtDescIdDesc(
            UUID userId,
            Pageable pageable
    );

    /**
     * Claim theo lease, giống hệt order_outbox: PENDING chưa ai lấy, hoặc
     * PROCESSING mà lease đã hết hạn (nhặt lại của instance nào đó đã chết
     * giữa chừng). FOR UPDATE SKIP LOCKED để nhiều instance chạy song song
     * không giành cùng một dòng.
     */
    @Query(
            value = """
                    select candidate.*
                    from notifications candidate
                    where (
                            candidate.status = 'PENDING'
                            or (
                                candidate.status = 'PROCESSING'
                                and candidate.locked_until <= :now
                            )
                        )
                    order by candidate.id
                    limit :batchSize
                    for update skip locked
                    """,
            nativeQuery = true
    )
    List<Notification> findClaimableForUpdate(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    /** Chỉ ghi khi lease vẫn còn thuộc đúng token đã claim -- tránh ghi đè
     *  kết quả của một lần claim khác đã lấy lại dòng này vì lease hết hạn. */
    @Modifying
    @Query("""
            update Notification n
            set n.status = :sent, n.lockToken = null, n.lockedUntil = null
            where n.id = :id and n.status = :processing and n.lockToken = :lockToken
            """)
    int markSent(
            @Param("id") Long id,
            @Param("lockToken") String lockToken,
            @Param("processing") NotificationStatus processing,
            @Param("sent") NotificationStatus sent
    );

    @Modifying
    @Query("""
            update Notification n
            set n.status = :nextStatus, n.attempts = :attempts,
                n.lockToken = null, n.lockedUntil = null
            where n.id = :id and n.status = :processing and n.lockToken = :lockToken
            """)
    int markFailedAttempt(
            @Param("id") Long id,
            @Param("lockToken") String lockToken,
            @Param("processing") NotificationStatus processing,
            @Param("nextStatus") NotificationStatus nextStatus,
            @Param("attempts") int attempts
    );
}
