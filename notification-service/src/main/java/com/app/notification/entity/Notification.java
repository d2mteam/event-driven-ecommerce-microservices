package com.app.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.util.UUID;

@Entity
@Table(
        name = "notifications",
        indexes = @Index(
                name = "idx_notifications_user_created",
                columnList = "user_id, created_at, id"
        ),
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

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private java.time.Instant createdAt;

    /**
     * Đổi nội dung khi order có trạng thái mới. Nội dung mới sẽ tạo một yêu
     * cầu gửi email mới qua Kafka.
     */
    public void replaceMessage(String message) {
        this.message = message;
    }
}
