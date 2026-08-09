package com.app.order.service;

import com.app.order.dto.OrderResponse;
import com.app.order.entity.Order;
import com.app.order.event.OrderCancellationRequestedEvent;
import com.app.order.event.OrderCancelledEvent;
import com.app.order.event.PaymentEventType;
import com.app.order.event.PaymentResultEvent;
import com.app.order.mapper.OrderMapper;
import com.app.order.model.OrderStatus;
import com.app.order.model.PaymentResultOutcome;
import com.app.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceTest {

    private static final UUID ORDER_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID USER_ID =
            UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderOutboxWriter outboxWriter;

    @Mock
    private OrderMapper orderMapper;

    @Test
    void requestsCancellationAndWritesOneOutboxEvent() {
        Order order = order(OrderStatus.CONFIRMED);
        OrderResponse response = response(OrderStatus.CANCEL_PENDING);
        when(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(response);
        OrderCancellationService service = service();

        OrderResponse result = service.request(USER_ID, ORDER_ID);

        assertThat(result).isSameAs(response);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_PENDING);
        ArgumentCaptor<OrderCancellationRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(OrderCancellationRequestedEvent.class);
        verify(outboxWriter).add(eventCaptor.capture(), eq(ORDER_ID));
        assertThat(eventCaptor.getValue().orderId()).isEqualTo(ORDER_ID);
        assertThat(eventCaptor.getValue().amount())
                .isEqualByComparingTo("199000.00");
    }

    @Test
    void repeatedRequestReturnsCurrentStateWithoutAnotherEvent() {
        Order order = order(OrderStatus.CANCEL_PENDING);
        OrderResponse response = response(OrderStatus.CANCEL_PENDING);
        when(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(response);

        OrderResponse result = service().request(USER_ID, ORDER_ID);

        assertThat(result).isSameAs(response);
        verify(outboxWriter, never()).add(any(), any());
    }

    @Test
    void rejectsCancellationBeforePaymentWasConfirmed() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service().request(USER_ID, ORDER_ID))
                .hasMessage("Only confirmed orders can be cancelled");

        verify(outboxWriter, never()).add(any(), any());
    }

    @Test
    void completesCancellationAfterRefundAndWritesCancelledEvent() {
        Order order = order(OrderStatus.CANCEL_PENDING);
        PaymentResultEvent event = refundedEvent();
        when(orderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        PaymentResultOutcome outcome = service().completeRefund(event);

        assertThat(outcome).isEqualTo(PaymentResultOutcome.APPLIED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outboxWriter).add(any(OrderCancelledEvent.class), any(UUID.class));
    }

    @Test
    void repeatedRefundEventDoesNotWriteAnotherCancelledEvent() {
        Order order = order(OrderStatus.CANCELLED);
        when(orderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        PaymentResultOutcome outcome = service().completeRefund(refundedEvent());

        assertThat(outcome).isEqualTo(PaymentResultOutcome.DUPLICATE);
        verify(outboxWriter, never()).add(any(), any());
    }

    private OrderCancellationService service() {
        return new OrderCancellationService(
                orderRepository,
                outboxWriter,
                orderMapper
        );
    }

    private Order order(OrderStatus status) {
        return Order.builder()
                .id(ORDER_ID)
                .userId(USER_ID)
                .reservationId(42L)
                .status(status)
                .totalPrice(new BigDecimal("199000.00"))
                .items(List.of())
                .createdAt(Instant.parse("2026-08-04T03:00:00Z"))
                .build();
    }

    private OrderResponse response(OrderStatus status) {
        return new OrderResponse(
                ORDER_ID,
                USER_ID,
                42L,
                status,
                null,
                new BigDecimal("199000.00"),
                List.of(),
                Instant.parse("2026-08-04T03:00:00Z")
        );
    }

    private PaymentResultEvent refundedEvent() {
        return new PaymentResultEvent(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                1,
                PaymentEventType.PAYMENT_REFUNDED,
                7L,
                ORDER_ID,
                new BigDecimal("199000.00"),
                Instant.parse("2026-08-04T04:00:00Z")
        );
    }
}
