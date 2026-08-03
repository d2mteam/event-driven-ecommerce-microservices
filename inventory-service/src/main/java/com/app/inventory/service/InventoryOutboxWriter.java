package com.app.inventory.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.app.inventory.config.InventoryMessagingProperties;
import com.app.inventory.entity.InventoryOutboxMessage;
import com.app.inventory.entity.InventoryOutboxStatus;
import com.app.inventory.entity.InventoryReservation;
import com.app.inventory.messaging.ReservationExpiredEvent;
import com.app.inventory.repository.InventoryOutboxMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class InventoryOutboxWriter {

    private final InventoryOutboxMessageRepository outboxRepository;
    private final InventoryMessagingProperties messagingProperties;
    private final ObjectMapper objectMapper;

    public void addReservationExpired(InventoryReservation reservation) {
        UUID messageId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        ReservationExpiredEvent event = ReservationExpiredEvent.from(
                messageId,
                reservation.getOrderId(),
                reservation.getId(),
                createdAt
        );

        outboxRepository.save(InventoryOutboxMessage.builder()
                .messageId(messageId)
                .topic(messagingProperties.inventoryEvents())
                .key(reservation.getOrderId().toString())
                .type(ReservationExpiredEvent.class.getSimpleName())
                .payload(serialize(event))
                .createdAt(createdAt)
                .status(InventoryOutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(createdAt)
                .build());
    }

    private String serialize(ReservationExpiredEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Cannot serialize ReservationExpiredEvent",
                    exception
            );
        }
    }
}
