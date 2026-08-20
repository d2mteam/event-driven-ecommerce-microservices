package com.app.notification.entity;

import com.app.notification.model.NotificationStatus;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(
                        name = "idx_notifications_user_created",
                        columnList = "user_id, created_at, id"
                ),
                // Sweeper claim theo status + locked_until, giống hệt lý do
                // order_outbox có idx_order_outbox_claim.
                @Index(
                        name = "idx_notifications_claim",
                        columnList = "status, locked_until, id"
                )
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notifications_order_id",
                columnNames = "order_id"
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private String message;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status = NotificationStatus.PENDING;

    /** Số lần đã thử gửi mail. Sweeper dựa vào đây để biết khi nào bỏ cuộc. */
    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    /** Cùng một token do sweeper cấp lúc claim -- dùng để bảo vệ ghi kết quả:
     *  chỉ ghi khi lease vẫn còn thuộc về đúng lần claim đó. */
    @Column(name = "lock_token", length = 36)
    private String lockToken;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Đổi nội dung khi có event mới cho cùng một order (vd: CONFIRMED bị ghi
     * đè bởi FAILED). Đặt lại PENDING và xoá số lần thử cũ -- nội dung đã đổi
     * thì phải gửi lại, không được coi là "đã gửi" từ trạng thái trước.
     */
    public void replaceMessage(String message) {
        this.message = message;
        this.status = NotificationStatus.PENDING;
        this.attempts = 0;
        this.lockToken = null;
        this.lockedUntil = null;
    }

    public void claim(String token, Instant leaseDeadline) {
        status = NotificationStatus.PROCESSING;
        lockToken = token;
        lockedUntil = leaseDeadline;
    }
}
