package com.app.order.service;

import com.app.order.dto.OrderResponse;
import com.app.order.entity.Order;
import com.app.order.event.EventVersions;
import com.app.order.event.OrderCancellationRequestedEvent;
import com.app.order.event.OrderCancelledEvent;
import com.app.order.event.OrderEventType;
import com.app.order.event.PaymentResultEvent;
import com.app.order.exception.OrderNotFoundException;
import com.app.order.exception.OrderStateConflictException;
import com.app.order.mapper.OrderMapper;
import com.app.order.model.OrderStatus;
import com.app.order.model.PaymentResultOutcome;
import com.app.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderCancellationService {

    private final OrderRepository orderRepository;
    private final OrderOutboxWriter outboxWriter;
    private final OrderMapper orderMapper;

    @Transactional
    public OrderResponse request(UUID userId, UUID orderId) {
        Order order = orderRepository
                .findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CANCEL_PENDING
                || order.getStatus() == OrderStatus.CANCELLED) {
            return orderMapper.toResponse(order);
        }
        if (!order.requestCancellation()) {
            throw new OrderStateConflictException(
                    "Only confirmed orders can be cancelled"
            );
        }

        Instant occurredAt = Instant.now();
        outboxWriter.add(new OrderCancellationRequestedEvent(
                UUID.randomUUID(),
                EventVersions.ORDER_CANCELLATION_REQUESTED,
                OrderEventType.ORDER_CANCELLATION_REQUESTED,
                order.getId(),
                order.getReservationId(),
                order.getTotalPrice(),
                occurredAt
        ), order.getId());
        return orderMapper.toResponse(order);
    }

    @Transactional
    public PaymentResultOutcome completeRefund(PaymentResultEvent event) {
        Order order = orderRepository.findByIdForUpdate(event.orderId())
                .orElse(null);
        if (order == null
                || order.getTotalPrice().compareTo(event.amount()) != 0) {
            return PaymentResultOutcome.INVARIANT_VIOLATION;
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return PaymentResultOutcome.DUPLICATE;
        }
        if (!order.completeCancellation()) {
            return PaymentResultOutcome.INVARIANT_VIOLATION;
        }

        outboxWriter.add(new OrderCancelledEvent(
                UUID.randomUUID(),
                EventVersions.ORDER_CANCELLED,
                OrderEventType.ORDER_CANCELLED,
                order.getId(),
                order.getUserId(),
                order.getReservationId(),
                Instant.now()
        ), order.getId());
        return PaymentResultOutcome.APPLIED;
    }
}
