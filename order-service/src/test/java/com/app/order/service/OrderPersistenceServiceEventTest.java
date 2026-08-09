package com.app.order.service;

import com.app.order.entity.Order;
import com.app.order.event.EventVersions;
import com.app.order.event.InventoryEventType;
import com.app.order.event.PaymentEventType;
import com.app.order.event.PaymentResultEvent;
import com.app.order.event.ReservationExpiredEvent;
import com.app.order.mapper.OrderMapper;
import com.app.order.model.OrderFailureReason;
import com.app.order.model.OrderStatus;
import com.app.order.model.PaymentResultOutcome;
import com.app.order.model.ReservationExpirationOutcome;
import com.app.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPersistenceServiceEventTest {

    private static final UUID ORDER_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderOutboxWriter outboxWriter;

    @InjectMocks
    private OrderPersistenceService persistenceService;

    @Test
    void appliesPaymentResultAndCreatesOutboxMessage() {
        Order order = order(OrderStatus.PENDING_PAYMENT, null, 42L);
        PaymentResultEvent event = paymentSucceeded();
        when(orderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));
        when(orderMapper.toEventItems(order.getItems()))
                .thenReturn(List.of());

        PaymentResultOutcome outcome =
                persistenceService.applyPaymentResult(event);

        assertThat(outcome).isEqualTo(PaymentResultOutcome.APPLIED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(outboxWriter).add(any(), any());
    }

    @Test
    void recognizesAlreadyAppliedPaymentAsDuplicate() {
        Order order = order(OrderStatus.CONFIRMED, null, 42L);
        when(orderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        PaymentResultOutcome outcome =
                persistenceService.applyPaymentResult(paymentSucceeded());

        assertThat(outcome).isEqualTo(PaymentResultOutcome.DUPLICATE);
        verify(outboxWriter, never()).add(any(), any());
    }

    @Test
    void rejectsPaymentAmountMismatchAsInvariantViolation() {
        Order order = order(OrderStatus.PENDING_PAYMENT, null, 42L);
        when(orderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));
        PaymentResultEvent event = new PaymentResultEvent(
                UUID.randomUUID(),
                EventVersions.PAYMENT_RESULT,
                PaymentEventType.PAYMENT_SUCCEEDED,
                7L,
                ORDER_ID,
                new BigDecimal("1.00"),
                Instant.parse("2026-08-04T04:00:00Z")
        );

        PaymentResultOutcome outcome =
                persistenceService.applyPaymentResult(event);

        assertThat(outcome)
                .isEqualTo(PaymentResultOutcome.INVARIANT_VIOLATION);
        verify(outboxWriter, never()).add(any(), any());
    }

    @Test
    void treatsMissingOrderForExpiredReservationAsOrphan() {
        ReservationExpiredEvent event = new ReservationExpiredEvent(
                UUID.randomUUID(),
                EventVersions.RESERVATION_EXPIRED,
                InventoryEventType.RESERVATION_EXPIRED,
                ORDER_ID,
                42L,
                Instant.parse("2026-08-04T04:00:00Z")
        );
        when(orderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.empty());

        ReservationExpirationOutcome outcome =
                persistenceService.failExpiredReservation(event);

        assertThat(outcome).isEqualTo(ReservationExpirationOutcome.ORPHAN);
        verify(outboxWriter, never()).add(any(), any());
    }

    private Order order(
            OrderStatus status,
            OrderFailureReason failureReason,
            long reservationId
    ) {
        return Order.builder()
                .id(ORDER_ID)
                .userId(UUID.fromString(
                        "cccccccc-cccc-cccc-cccc-cccccccccccc"
                ))
                .totalPrice(new BigDecimal("199000.00"))
                .status(status)
                .reservationId(reservationId)
                .failureReason(failureReason)
                .createdAt(Instant.parse("2026-08-04T03:00:00Z"))
                .items(List.of())
                .build();
    }

    private PaymentResultEvent paymentSucceeded() {
        return new PaymentResultEvent(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                EventVersions.PAYMENT_RESULT,
                PaymentEventType.PAYMENT_SUCCEEDED,
                7L,
                ORDER_ID,
                new BigDecimal("199000.00"),
                Instant.parse("2026-08-04T04:00:00Z")
        );
    }
}
