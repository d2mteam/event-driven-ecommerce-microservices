package com.app.inventory.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import com.app.inventory.entity.InventoryReservation;
import com.app.inventory.entity.ReservationStatus;

import jakarta.persistence.LockModeType;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    Optional<InventoryReservation> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from InventoryReservation reservation where reservation.id = :id")
    Optional<InventoryReservation> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reservation
            from InventoryReservation reservation
            where reservation.status = :status
              and reservation.expiresAt <= :expiresAt
            order by reservation.id
            """)
    List<InventoryReservation> findExpiredForUpdate(
            @Param("status") ReservationStatus status,
            @Param("expiresAt") Instant expiresAt
    );

    List<InventoryReservation>
            findByStatusAndExpirationEventIdIsNotNullAndExpirationEventPublishedAtIsNullOrderByIdAsc(
                    ReservationStatus status,
                    Pageable pageable
            );
}
