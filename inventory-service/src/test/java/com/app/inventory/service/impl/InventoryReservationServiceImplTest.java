package com.app.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.app.inventory.config.InventoryReservationProperties;
import com.app.inventory.entity.Inventory;
import com.app.inventory.entity.InventoryReservation;
import com.app.inventory.entity.ReservationItem;
import com.app.inventory.entity.ReservationStatus;
import com.app.inventory.exception.InventoryEventConflictException;
import com.app.inventory.mapper.InventoryReservationMapper;
import com.app.inventory.messaging.EventVersions;
import com.app.inventory.messaging.OrderConfirmedEvent;
import com.app.inventory.messaging.OrderEventType;
import com.app.inventory.messaging.OrderFailedEvent;
import com.app.inventory.messaging.OrderCancelledEvent;
import com.app.inventory.repository.InventoryRepository;
import com.app.inventory.repository.InventoryReservationRepository;
import com.app.inventory.service.InventoryOutboxWriter;
import com.app.inventory.config.InventoryStockFilterProperties;
import com.app.inventory.service.InventoryStockFilter;
import com.app.inventory.service.ReservationItemNormalizer;

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
                    outboxWriter,
                    new InventoryStockFilter(null, new InventoryStockFilterProperties()),
                    new ReservationItemNormalizer()
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

    @Test
    void treatsRepeatedConfirmationAsAlreadyApplied() {
        InventoryReservation reservation = heldReservation(
                List.of(new ReservationItem(1L, 1))
        );
        reservation.settle();
        OrderConfirmedEvent event = confirmedEvent(reservation.getOrderId());
        when(reservationRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(reservation));

        service.settleConfirmedOrder(event);

        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void treatsRepeatedFailureAsAlreadyApplied() {
        InventoryReservation reservation = heldReservation(
                List.of(new ReservationItem(1L, 1))
        );
        reservation.release();
        OrderFailedEvent event = failedEvent(reservation.getOrderId());
        when(reservationRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(reservation));

        service.releaseFailedOrder(event);

        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void rejectsConfirmationAfterReservationWasReleased() {
        InventoryReservation reservation = heldReservation(
                List.of(new ReservationItem(1L, 1))
        );
        reservation.release();
        OrderConfirmedEvent event = confirmedEvent(reservation.getOrderId());
        when(reservationRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.settleConfirmedOrder(event))
                .isInstanceOf(InventoryEventConflictException.class)
                .hasMessage("Cannot settle reservation in status RELEASED");

        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void rejectsEventForAnotherOrder() {
        InventoryReservation reservation = heldReservation(
                List.of(new ReservationItem(1L, 1))
        );
        OrderConfirmedEvent event = confirmedEvent(UUID.randomUUID());
        when(reservationRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.settleConfirmedOrder(event))
                .isInstanceOf(InventoryEventConflictException.class)
                .hasMessage("Reservation does not belong to the event order");

        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void returnsSettledReservationToStockOnlyOnce() {
        InventoryReservation reservation = heldReservation(
                List.of(new ReservationItem(1L, 2))
        );
        reservation.settle();
        Inventory inventory = inventory(1L, 0);
        OrderCancelledEvent event = cancelledEvent(reservation.getOrderId());
        when(reservationRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(reservation));
        when(inventoryRepository.findAllByProductIdForUpdate(List.of(1L)))
                .thenReturn(List.of(inventory));

        service.returnCancelledOrder(event);

        assertThat(inventory.getOnHandQuantity()).isEqualTo(12);
        assertThat(inventory.getReservedQuantity()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RETURNED);

        service.returnCancelledOrder(event);

        assertThat(inventory.getOnHandQuantity()).isEqualTo(12);
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

    private OrderConfirmedEvent confirmedEvent(UUID orderId) {
        return new OrderConfirmedEvent(
                UUID.randomUUID(),
                EventVersions.ORDER_CONFIRMED,
                OrderEventType.ORDER_CONFIRMED,
                orderId,
                UUID.randomUUID(),
                42L,
                BigDecimal.TEN,
                List.of(),
                Instant.parse("2026-08-04T04:00:00Z")
        );
    }

    private OrderFailedEvent failedEvent(UUID orderId) {
        return new OrderFailedEvent(
                UUID.randomUUID(),
                EventVersions.ORDER_FAILED,
                OrderEventType.ORDER_FAILED,
                orderId,
                UUID.randomUUID(),
                42L,
                "PAYMENT_FAILED",
                Instant.parse("2026-08-04T04:00:00Z")
        );
    }

    private OrderCancelledEvent cancelledEvent(UUID orderId) {
        return new OrderCancelledEvent(
                UUID.randomUUID(),
                EventVersions.ORDER_CANCELLED,
                OrderEventType.ORDER_CANCELLED,
                orderId,
                UUID.randomUUID(),
                42L,
                Instant.parse("2026-08-04T05:00:00Z")
        );
    }
}
