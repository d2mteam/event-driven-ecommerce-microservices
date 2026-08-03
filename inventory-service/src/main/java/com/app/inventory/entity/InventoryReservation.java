package com.app.inventory.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "inventory_reservations",
        indexes = @Index(
                name = "idx_inventory_reservations_expiry",
                columnList = "status, expires_at, id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID orderId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private List<ReservationItem> items;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(unique = true)
    private UUID expirationEventId;

    private Instant expirationEventPublishedAt;

    public static InventoryReservation held(
            UUID orderId,
            List<ReservationItem> items,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new InventoryReservation(
                null,
                orderId,
                List.copyOf(items),
                ReservationStatus.HELD,
                expiresAt,
                createdAt,
                null,
                null
        );
    }

    public boolean isHeld() {
        return status == ReservationStatus.HELD;
    }

    public void settle() {
        if (isHeld()) {
            status = ReservationStatus.SETTLED;
        }
    }

    public void release() {
        if (isHeld()) {
            status = ReservationStatus.RELEASED;
        }
    }

    public void expire(UUID eventId) {
        if (isHeld()) {
            status = ReservationStatus.EXPIRED;
            expirationEventId = eventId;
        }
    }

    public void markExpirationEventPublished(Instant publishedAt) {
        expirationEventPublishedAt = publishedAt;
    }
}
