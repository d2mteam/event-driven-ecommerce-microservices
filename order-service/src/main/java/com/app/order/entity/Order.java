package com.app.order.entity;

import com.app.order.model.OrderItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import com.app.order.model.OrderStatus;
import com.app.order.model.OrderFailureReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(
                        name = "idx_orders_user_created_at",
                        columnList = "user_id, created_at"
                ),
                @Index(
                        name = "idx_orders_status_created_at",
                        columnList = "status, created_at"
                ),
                @Index(
                        name = "idx_orders_created_at",
                        columnList = "created_at"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private OrderStatus status;

    @Column(nullable = false)
    private Long reservationId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(64)")
    private OrderFailureReason failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private List<OrderItem> items = new ArrayList<>();

    public boolean confirmPayment() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            return false;
        }

        status = OrderStatus.CONFIRMED;
        failureReason = null;
        return true;
    }

    public boolean failPayment(OrderFailureReason reason) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            return false;
        }

        status = OrderStatus.FAILED;
        failureReason = reason;
        return true;
    }

    public boolean failExpiredReservation(Long expiredReservationId) {
        if (!Objects.equals(reservationId, expiredReservationId)) {
            return false;
        }
        if (status != OrderStatus.PENDING_PAYMENT
                && status != OrderStatus.CONFIRMED) {
            return false;
        }

        status = OrderStatus.FAILED;
        failureReason = OrderFailureReason.RESERVATION_EXPIRED;
        return true;
    }
}
