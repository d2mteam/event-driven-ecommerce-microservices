package com.app.inventory.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.app.inventory.service.InventoryReservationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final ObjectMapper objectMapper;
    private final InventoryReservationService reservationService;

    @KafkaListener(
            topics = "${app.messaging.topics.order-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String payload) {
        OrderCreatedEvent event;
        try {
            event = objectMapper.readValue(payload, OrderCreatedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Invalid OrderCreatedEvent payload",
                    exception
            );
        }

        if (event.eventVersion() != EventVersions.ORDER_CREATED) {
            log.warn(
                    "Skip OrderCreatedEvent {} because version {} is not supported",
                    event.messageId(),
                    event.eventVersion()
            );
            return;
        }
        if (event.reservationId() == null) {
            log.warn(
                    "Skip OrderCreatedEvent {} because reservationId is missing",
                    event.messageId()
            );
            return;
        }

        reservationService.settle(event);
    }
}
