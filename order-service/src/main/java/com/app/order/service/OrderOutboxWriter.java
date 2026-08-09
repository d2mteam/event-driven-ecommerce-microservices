package com.app.order.service;

import com.app.order.config.KafkaMessagingProperties;
import com.app.order.entity.OutboxMessage;
import com.app.order.event.OutboxPayload;
import com.app.order.exception.MessageSerializationException;
import com.app.order.model.OutboxStatus;
import com.app.order.repository.OutboxMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderOutboxWriter {

    private final OutboxMessageRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final KafkaMessagingProperties kafkaProperties;

    public void add(OutboxPayload event, UUID orderId) {
        Instant createdAt = Instant.now();
        outboxRepository.save(OutboxMessage.builder()
                .messageId(event.messageId())
                .topic(kafkaProperties.getTopics().getOrderEvents())
                .key(orderId.toString())
                .type(event.getClass().getSimpleName())
                .payload(serialize(event))
                .createdAt(createdAt)
                .status(OutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(createdAt)
                .build());
    }

    private String serialize(OutboxPayload event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new MessageSerializationException(
                    "Cannot serialize " + event.getClass().getSimpleName(),
                    exception
            );
        }
    }
}
