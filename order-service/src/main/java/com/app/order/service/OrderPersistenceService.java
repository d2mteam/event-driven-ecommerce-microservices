package com.app.order.service;

import com.app.order.config.KafkaMessagingProperties;
import com.app.order.entity.Order;
import com.app.order.entity.OutboxMessage;
import com.app.order.event.OrderCreatedEvent;
import com.app.order.event.EventVersions;
import com.app.order.event.OrderEventType;
import com.app.order.event.OrderFailedEvent;
import com.app.order.event.OutboxPayload;
import com.app.order.event.ReservationExpiredEvent;
import com.app.order.exception.MessageSerializationException;
import com.app.order.repository.OrderRepository;
import com.app.order.repository.OutboxMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;
    private final KafkaMessagingProperties kafkaProperties;

    @Transactional
    public Order save(
            Order order,
            OrderCreatedEvent orderCreated
    ) {
        Order savedOrder = orderRepository.save(order);
        outboxMessageRepository.save(toOutboxMessage(
                kafkaProperties.getTopics().getOrderEvents(),
                orderCreated.orderId().toString(),
                orderCreated
        ));
        return savedOrder;
    }

    @Transactional
    public boolean failExpiredReservation(ReservationExpiredEvent event) {
        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null || !order.failExpiredReservation(event.reservationId())) {
            return false;
        }

        OrderFailedEvent orderFailed = new OrderFailedEvent(
                UUID.randomUUID(),
                EventVersions.ORDER_FAILED,
                OrderEventType.ORDER_FAILED,
                order.getId(),
                order.getUserId(),
                order.getReservationId(),
                order.getFailureReason(),
                Instant.now()
        );
        outboxMessageRepository.save(toOutboxMessage(
                kafkaProperties.getTopics().getOrderEvents(),
                order.getId().toString(),
                orderFailed
        ));
        return true;
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
