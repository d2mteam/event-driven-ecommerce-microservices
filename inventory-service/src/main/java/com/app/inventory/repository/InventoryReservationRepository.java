package com.app.inventory.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;

import com.app.inventory.entity.InventoryReservation;
import com.app.inventory.entity.ReservationStatus;

import jakarta.persistence.LockModeType;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    Optional<InventoryReservation> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from InventoryReservation reservation where reservation.id = :id")
    Optional<InventoryReservation> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select *
            from inventory_reservations
            where status = :status
              and expires_at <= :expiresAt
            order by expires_at, id
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<InventoryReservation> findExpiredForUpdate(
            @Param("status") String status,
            @Param("expiresAt") Instant expiresAt,
            @Param("batchSize") int batchSize
    );

    List<InventoryReservation>
            findByStatusAndExpirationEventIdIsNotNullAndExpirationEventPublishedAtIsNullOrderByIdAsc(
                    ReservationStatus status,
                    Pageable pageable
            );
}
