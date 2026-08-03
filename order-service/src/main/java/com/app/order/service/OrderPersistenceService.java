package com.app.order.service;

import com.app.order.config.KafkaMessagingProperties;
import com.app.order.entity.Order;
import com.app.order.entity.OutboxMessage;
import com.app.order.event.OrderConfirmedEvent;
import com.app.order.event.EventVersions;
import com.app.order.event.OrderEventType;
import com.app.order.event.OrderFailedEvent;
import com.app.order.event.OutboxPayload;
import com.app.order.event.PaymentResultEvent;
import com.app.order.event.ReservationExpiredEvent;
import com.app.order.exception.MessageSerializationException;
import com.app.order.mapper.OrderMapper;
import com.app.order.model.IdempotencyStatus;
import com.app.order.model.OrderFailureReason;
import com.app.order.repository.OrderRepository;
import com.app.order.repository.OrderIdempotencyRepository;
import com.app.order.repository.OutboxMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final OrderIdempotencyRepository idempotencyRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;
    private final KafkaMessagingProperties kafkaProperties;
    private final OrderMapper orderMapper;

    @Transactional
    public Order save(
            Order order,
            Long idempotencyId
    ) {
        Order savedOrder = orderRepository.save(order);
        int updated = idempotencyRepository.changeStatus(
                idempotencyId,
                IdempotencyStatus.PROCESSING,
                IdempotencyStatus.COMPLETED
        );
        if (updated != 1) {
            throw new IllegalStateException(
                    "Idempotency record is not processing"
            );
        }
        return savedOrder;
    }

    @Transactional
    public boolean applyPaymentResult(PaymentResultEvent event) {
        Order order = orderRepository.findByIdForUpdate(event.orderId())
                .orElse(null);
        if (order == null) {
            log.warn(
                    "Skip payment event {} because order {} does not exist",
                    event.messageId(),
                    event.orderId()
            );
            return false;
        }
        if (event.amount() == null
                || order.getTotalPrice().compareTo(event.amount()) != 0) {
            log.warn(
                    "Skip payment event {} because amount does not match order {}",
                    event.messageId(),
                    event.orderId()
            );
            return false;
        }

        return switch (event.eventType()) {
            case PAYMENT_SUCCEEDED -> confirmOrder(order);
            case PAYMENT_FAILED -> failOrder(order, OrderFailureReason.PAYMENT_FAILED);
            case PAYMENT_EXPIRED -> failOrder(order, OrderFailureReason.PAYMENT_EXPIRED);
        };
    }

    @Transactional
    public boolean failExpiredReservation(ReservationExpiredEvent event) {
        Order order = orderRepository.findByIdForUpdate(event.orderId())
                .orElse(null);
        if (order == null || !order.failExpiredReservation(event.reservationId())) {
            return false;
        }

        saveOrderFailedEvent(order);
        return true;
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
        outboxMessageRepository.save(toOutboxMessage(
                kafkaProperties.getTopics().getOrderEvents(),
                order.getId().toString(),
                orderConfirmed
        ));
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
        outboxMessageRepository.save(toOutboxMessage(
                kafkaProperties.getTopics().getOrderEvents(),
                order.getId().toString(),
                orderFailed
        ));
    }

    private OutboxMessage toOutboxMessage(
            String topic,
            String key,
            OutboxPayload message
    ) {
        return OutboxMessage.builder()
                .messageId(message.messageId())
                .topic(topic)
                .key(key)
                .type(message.getClass().getSimpleName())
                .payload(serialize(message))
                .createdAt(Instant.now())
                .build();
    }

    private String serialize(Object message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new MessageSerializationException(
                    "Cannot serialize " + message.getClass().getSimpleName(),
                    exception
            );
        }
    }
}
