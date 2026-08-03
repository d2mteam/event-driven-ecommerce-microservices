package com.app.inventory.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.app.inventory.service.InventoryReservationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
        JsonNode eventJson;
        try {
            eventJson = objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Invalid order event payload",
                    exception
            );
        }

        String eventType = eventJson.path("eventType").asText();
        if (OrderEventType.ORDER_CONFIRMED.name().equals(eventType)) {
            consumeOrderConfirmed(eventJson);
            return;
        }
        if (OrderEventType.ORDER_FAILED.name().equals(eventType)) {
            consumeOrderFailed(eventJson);
            return;
        }
        log.debug("Skip order event type {}", eventType);
    }

    private void consumeOrderConfirmed(JsonNode eventJson) {
        OrderConfirmedEvent event;
        try {
            event = objectMapper.treeToValue(eventJson, OrderConfirmedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Invalid OrderConfirmedEvent payload",
                    exception
            );
        }

        if (event.eventVersion() != EventVersions.ORDER_CONFIRMED) {
            log.warn(
                    "Skip OrderConfirmedEvent {} because version {} is not supported",
                    event.messageId(),
                    event.eventVersion()
            );
            return;
        }
        if (event.reservationId() == null) {
            log.warn(
                    "Skip OrderConfirmedEvent {} because reservationId is missing",
                    event.messageId()
            );
            return;
        }

        reservationService.settleConfirmedOrder(event);
    }

    private void consumeOrderFailed(JsonNode eventJson) {
        OrderFailedEvent event;
        try {
            event = objectMapper.treeToValue(eventJson, OrderFailedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Invalid OrderFailedEvent payload",
                    exception
            );
        }

        if (event.eventVersion() != EventVersions.ORDER_FAILED) {
            log.warn(
                    "Skip OrderFailedEvent {} because version {} is not supported",
                    event.messageId(),
                    event.eventVersion()
            );
            return;
        }
        if (event.reservationId() == null) {
            log.warn(
                    "Skip OrderFailedEvent {} because reservationId is missing",
                    event.messageId()
            );
            return;
        }

        reservationService.releaseFailedOrder(event);
    }
}
