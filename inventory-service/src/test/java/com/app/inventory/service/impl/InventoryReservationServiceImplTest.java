package com.app.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.app.inventory.config.InventoryReservationProperties;
import com.app.inventory.entity.Inventory;
import com.app.inventory.entity.InventoryReservation;
import com.app.inventory.entity.ReservationItem;
import com.app.inventory.entity.ReservationStatus;
import com.app.inventory.mapper.InventoryReservationMapper;
import com.app.inventory.repository.InventoryRepository;
import com.app.inventory.repository.InventoryReservationRepository;
import com.app.inventory.service.InventoryOutboxWriter;

class InventoryReservationServiceImplTest {

    private final InventoryRepository inventoryRepository =
            mock(InventoryRepository.class);
    private final InventoryReservationRepository reservationRepository =
            mock(InventoryReservationRepository.class);
    private final InventoryOutboxWriter outboxWriter =
            mock(InventoryOutboxWriter.class);

    private final InventoryReservationServiceImpl service =
            new InventoryReservationServiceImpl(
                    inventoryRepository,
                    reservationRepository,
                    mock(InventoryReservationProperties.class),
                    mock(InventoryReservationMapper.class),
                    outboxWriter
            );

    @Test
    void releasesOneBatchAndLocksProductsInAscendingOrder() {
        Instant expiresAt = Instant.parse("2026-08-04T01:00:00Z");
        InventoryReservation first = heldReservation(
                List.of(new ReservationItem(2L, 1))
        );
        InventoryReservation second = heldReservation(
                List.of(
                        new ReservationItem(2L, 2),
                        new ReservationItem(1L, 3)
                )
        );
        Inventory productOne = inventory(1L, 3);
        Inventory productTwo = inventory(2L, 3);

        when(reservationRepository.findExpiredForUpdate("HELD", expiresAt, 50))
                .thenReturn(List.of(first, second));
        when(inventoryRepository.findAllByProductIdForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(productOne, productTwo));

        int released = service.releaseExpiredReservations(expiresAt, 50);

        assertThat(released).isEqualTo(2);
        assertThat(productOne.getReservedQuantity()).isZero();
        assertThat(productTwo.getReservedQuantity()).isZero();
        assertThat(first.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(second.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        verify(inventoryRepository).findAllByProductIdForUpdate(List.of(1L, 2L));
        verify(outboxWriter).addReservationExpired(first);
        verify(outboxWriter).addReservationExpired(second);
    }

    private InventoryReservation heldReservation(List<ReservationItem> items) {
        Instant createdAt = Instant.parse("2026-08-04T00:00:00Z");
        return InventoryReservation.held(
                UUID.randomUUID(),
                items,
                createdAt,
                createdAt.plusSeconds(60)
        );
    }

    private Inventory inventory(long productId, int reservedQuantity) {
        return Inventory.builder()
                .productId(productId)
                .onHandQuantity(10)
                .reservedQuantity(reservedQuantity)
                .build();
    }
}
