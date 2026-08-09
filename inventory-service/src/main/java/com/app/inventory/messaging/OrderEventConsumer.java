package com.app.inventory.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.app.inventory.service.InventoryReservationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

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
        JsonNode eventJson = readEvent(payload);
        String eventType = requiredText(eventJson, "eventType");

        if (OrderEventType.ORDER_CONFIRMED.name().equals(eventType)) {
            consumeOrderConfirmed(eventJson);
            return;
        }
        if (OrderEventType.ORDER_FAILED.name().equals(eventType)) {
            consumeOrderFailed(eventJson);
            return;
        }
        if (OrderEventType.ORDER_CANCELLED.name().equals(eventType)) {
            consumeOrderCancelled(eventJson);
            return;
        }
        if (OrderEventType.ORDER_CANCELLATION_REQUESTED.name()
                .equals(eventType)) {
            return;
        }
        throw invalidEvent("Unknown order event type: " + eventType);
    }

    private void consumeOrderConfirmed(JsonNode eventJson) {
        OrderConfirmedEvent event = readEvent(
                eventJson,
                OrderConfirmedEvent.class
        );
        if (event.eventVersion() != EventVersions.ORDER_CONFIRMED) {
            throw invalidEvent(
                    "Unsupported OrderConfirmedEvent version: "
                            + event.eventVersion()
            );
        }

        reservationService.settleConfirmedOrder(event);
    }

    private void consumeOrderFailed(JsonNode eventJson) {
        OrderFailedEvent event = readEvent(eventJson, OrderFailedEvent.class);
        if (event.eventVersion() != EventVersions.ORDER_FAILED) {
            throw invalidEvent(
                    "Unsupported OrderFailedEvent version: "
                            + event.eventVersion()
            );
        }

        reservationService.releaseFailedOrder(event);
    }

    private void consumeOrderCancelled(JsonNode eventJson) {
        OrderCancelledEvent event = readEvent(
                eventJson,
                OrderCancelledEvent.class
        );
        if (event.eventVersion() != EventVersions.ORDER_CANCELLED) {
            throw invalidEvent(
                    "Unsupported OrderCancelledEvent version: "
                            + event.eventVersion()
            );
        }

        reservationService.returnCancelledOrder(event);
    }

    private JsonNode readEvent(String payload) {
        if (payload == null) {
            throw invalidEvent("Order event payload is null");
        }
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new NonRetryableOrderEventException(
                    "Malformed order event JSON",
                    exception
            );
        }
    }

    private <T> T readEvent(JsonNode eventJson, Class<T> eventType) {
        try {
            return objectMapper.treeToValue(eventJson, eventType);
        } catch (JsonProcessingException exception) {
            throw new NonRetryableOrderEventException(
                    "Invalid " + eventType.getSimpleName(),
                    exception
            );
        }
    }

    private String requiredText(JsonNode eventJson, String field) {
        if (eventJson == null || !eventJson.isObject()
                || !eventJson.hasNonNull(field)) {
            throw invalidEvent("Missing required field: " + field);
        }
        String value = eventJson.get(field).asText();
        if (value.isBlank()) {
            throw invalidEvent("Required field is blank: " + field);
        }
        return value;
    }


    private NonRetryableOrderEventException invalidEvent(String message) {
        return new NonRetryableOrderEventException(message);
    }
}
