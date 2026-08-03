package com.app.notification.messaging;

import com.app.notification.event.EventVersions;
import com.app.notification.event.OrderConfirmedEvent;
import com.app.notification.event.OrderEventType;
import com.app.notification.event.OrderFailedEvent;
import com.app.notification.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${app.messaging.topics.order-events}",
            groupId = "${app.messaging.notification.group-id}"
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
        throw invalidEvent("Unknown order event type: " + eventType);
    }

    private void consumeOrderConfirmed(JsonNode eventJson) {
        requireFields(
                eventJson,
                "messageId",
                "eventVersion",
                "orderId",
                "userId",
                "reservationId",
                "totalPrice",
                "items",
                "occurredAt"
        );
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
        notificationService.createSuccessFor(event);
    }

    private void consumeOrderFailed(JsonNode eventJson) {
        requireFields(
                eventJson,
                "messageId",
                "eventVersion",
                "orderId",
                "userId",
                "reservationId",
                "reason",
                "occurredAt"
        );
        OrderFailedEvent event = readEvent(eventJson, OrderFailedEvent.class);
        if (event.eventVersion() != EventVersions.ORDER_FAILED) {
            throw invalidEvent(
                    "Unsupported OrderFailedEvent version: "
                            + event.eventVersion()
            );
        }
        notificationService.replaceWithFailure(event);
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
        requireFields(eventJson, field);
        String value = eventJson.get(field).asText();
        if (value.isBlank()) {
            throw invalidEvent("Required field is blank: " + field);
        }
        return value;
    }

    private void requireFields(JsonNode eventJson, String... fields) {
        if (eventJson == null || !eventJson.isObject()) {
            throw invalidEvent("Order event must be a JSON object");
        }
        for (String field : fields) {
            if (!eventJson.hasNonNull(field)) {
                throw invalidEvent("Missing required field: " + field);
            }
        }
    }

    private NonRetryableOrderEventException invalidEvent(String message) {
        return new NonRetryableOrderEventException(message);
    }
}
