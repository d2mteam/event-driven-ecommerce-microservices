package com.app.order.service;

import com.app.order.entity.Order;
import com.app.order.event.OrderConfirmedEvent;
import com.app.order.event.EventVersions;
import com.app.order.event.OrderEventType;
import com.app.order.event.OrderFailedEvent;
import com.app.order.event.PaymentEventType;
import com.app.order.event.PaymentResultEvent;
import com.app.order.event.ReservationExpiredEvent;
import com.app.order.mapper.OrderMapper;
import com.app.order.model.OrderFailureReason;
import com.app.order.model.OrderStatus;
import com.app.order.model.PaymentResultOutcome;
import com.app.order.model.ReservationExpirationOutcome;
import com.app.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderOutboxWriter outboxWriter;

    @Transactional
    public Order save(Order order) {
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID orderId) {
        return orderRepository.findById(orderId);
    }

    @Transactional
    public PaymentResultOutcome applyPaymentResult(PaymentResultEvent event) {
        if (event.eventType() == PaymentEventType.PAYMENT_REFUNDED) {
            return PaymentResultOutcome.INVARIANT_VIOLATION;
        }

        Order order = orderRepository.findByIdForUpdate(event.orderId())
                .orElse(null);
        if (order == null) {
            log.warn(
                    "Skip payment event {} because order {} does not exist",
                    event.messageId(),
                    event.orderId()
            );
            return PaymentResultOutcome.INVARIANT_VIOLATION;
        }
        if (event.amount() == null
                || order.getTotalPrice().compareTo(event.amount()) != 0) {
            log.warn(
                    "Skip payment event {} because amount does not match order {}",
                    event.messageId(),
                    event.orderId()
            );
            return PaymentResultOutcome.INVARIANT_VIOLATION;
        }

        if (event.eventType() == PaymentEventType.PAYMENT_SUCCEEDED
                && order.getStatus() == OrderStatus.CONFIRMED) {
            return PaymentResultOutcome.DUPLICATE;
        }

        OrderFailureReason expectedFailure = switch (event.eventType()) {
            case PAYMENT_SUCCEEDED -> null;
            case PAYMENT_FAILED -> OrderFailureReason.PAYMENT_FAILED;
            case PAYMENT_EXPIRED -> OrderFailureReason.PAYMENT_EXPIRED;
            case PAYMENT_REFUNDED -> null;
        };
        if (expectedFailure != null
                && order.getStatus() == OrderStatus.FAILED
                && order.getFailureReason() == expectedFailure) {
            return PaymentResultOutcome.DUPLICATE;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return PaymentResultOutcome.INVARIANT_VIOLATION;
        }

        boolean changed = switch (event.eventType()) {
            case PAYMENT_SUCCEEDED -> confirmOrder(order);
            case PAYMENT_FAILED -> failOrder(order, OrderFailureReason.PAYMENT_FAILED);
            case PAYMENT_EXPIRED -> failOrder(order, OrderFailureReason.PAYMENT_EXPIRED);
            case PAYMENT_REFUNDED -> false;
        };
        return changed
                ? PaymentResultOutcome.APPLIED
                : PaymentResultOutcome.INVARIANT_VIOLATION;
    }

    @Transactional
    public ReservationExpirationOutcome failExpiredReservation(
            ReservationExpiredEvent event
    ) {
        Order order = orderRepository.findByIdForUpdate(event.orderId())
                .orElse(null);
        if (order == null) {
            return ReservationExpirationOutcome.ORPHAN;
        }
        if (!Objects.equals(order.getReservationId(), event.reservationId())) {
            return ReservationExpirationOutcome.INVARIANT_VIOLATION;
        }
        if (order.getStatus() == OrderStatus.FAILED
                && order.getFailureReason()
                == OrderFailureReason.RESERVATION_EXPIRED) {
            return ReservationExpirationOutcome.DUPLICATE;
        }
        if (!order.failExpiredReservation(event.reservationId())) {
            return ReservationExpirationOutcome.INVARIANT_VIOLATION;
        }

        saveOrderFailedEvent(order);
        return ReservationExpirationOutcome.APPLIED;
    }

    private boolean confirmOrder(Order order) {
        if (!order.confirmPayment()) {
            return false;
        }

        Instant occurredAt = Instant.now();
        OrderConfirmedEvent orderConfirmed = OrderConfirmedEvent.builder()
                .messageId(UUID.randomUUID())
                .eventVersion(EventVersions.ORDER_CONFIRMED)
                .eventType(OrderEventType.ORDER_CONFIRMED)
                .orderId(order.getId())
                .userId(order.getUserId())
                .reservationId(order.getReservationId())
                .totalPrice(order.getTotalPrice())
                .items(orderMapper.toEventItems(order.getItems()))
                .occurredAt(occurredAt)
                .build();
        outboxWriter.add(orderConfirmed, order.getId());
        return true;
    }

    private boolean failOrder(
            Order order,
            OrderFailureReason failureReason
    ) {
        if (!order.failPayment(failureReason)) {
            return false;
        }

        saveOrderFailedEvent(order);
        return true;
    }

    private void saveOrderFailedEvent(Order order) {
        OrderFailedEvent orderFailed = OrderFailedEvent.builder()
                .messageId(UUID.randomUUID())
                .eventVersion(EventVersions.ORDER_FAILED)
                .eventType(OrderEventType.ORDER_FAILED)
                .orderId(order.getId())
                .userId(order.getUserId())
                .reservationId(order.getReservationId())
                .reason(order.getFailureReason())
                .occurredAt(Instant.now())
                .build();
        outboxWriter.add(orderFailed, order.getId());
    }
}
