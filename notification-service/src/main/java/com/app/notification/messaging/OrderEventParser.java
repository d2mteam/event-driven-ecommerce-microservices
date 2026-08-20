package com.app.notification.messaging;

import com.app.notification.config.NotificationProperties;
import com.app.notification.entity.Notification;
import com.app.notification.event.EventVersions;
import com.app.notification.event.OrderCancelledEvent;
import com.app.notification.event.OrderConfirmedEvent;
import com.app.notification.event.OrderEventType;
import com.app.notification.event.OrderFailedEvent;
import com.app.notification.exception.NonRetryableOrderEventException;
import com.app.notification.mapper.NotificationMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderEventParser {

    private final ObjectMapper objectMapper;
    private final NotificationMapper notificationMapper;
    private final NotificationProperties notificationProperties;

    public Optional<Notification> parse(String payload) {
        JsonNode eventJson = readJson(payload);
        OrderEventType eventType = readEventType(eventJson);

        return switch (eventType) {
            case ORDER_CONFIRMED -> Optional.of(confirmedNotification(eventJson));
            case ORDER_FAILED -> Optional.of(failedNotification(eventJson));
            case ORDER_CANCELLED -> Optional.of(cancelledNotification(eventJson));
            case ORDER_CANCELLATION_REQUESTED -> Optional.empty();
        };
    }

    private Notification confirmedNotification(JsonNode eventJson) {
        OrderConfirmedEvent event = readEvent(eventJson, OrderConfirmedEvent.class);
        requireVersion(event.eventVersion(), EventVersions.ORDER_CONFIRMED);
        return notificationMapper.toNotification(event, notificationProperties);
    }

    private Notification failedNotification(JsonNode eventJson) {
        OrderFailedEvent event = readEvent(eventJson, OrderFailedEvent.class);
        requireVersion(event.eventVersion(), EventVersions.ORDER_FAILED);
        return notificationMapper.toNotification(event, notificationProperties);
    }

    private Notification cancelledNotification(JsonNode eventJson) {
        OrderCancelledEvent event = readEvent(eventJson, OrderCancelledEvent.class);
        requireVersion(event.eventVersion(), EventVersions.ORDER_CANCELLED);
        return notificationMapper.toNotification(event, notificationProperties);
    }

    private JsonNode readJson(String payload) {
        if (payload == null) {
            throw invalidEvent("Order event payload is null");
        }
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new NonRetryableOrderEventException("Malformed order event JSON", exception);
        }
    }

    private <T> T readEvent(JsonNode eventJson, Class<T> eventType) {
        try {
            return objectMapper.treeToValue(eventJson, eventType);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new NonRetryableOrderEventException(
                    "Invalid " + eventType.getSimpleName(),
                    exception
            );
        }
    }

    private OrderEventType readEventType(JsonNode eventJson) {
        if (eventJson == null || !eventJson.isObject() || !eventJson.hasNonNull("eventType")) {
            throw invalidEvent("Missing required field: eventType");
        }
        String value = eventJson.get("eventType").asText();
        try {
            return OrderEventType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new NonRetryableOrderEventException(
                    "Unknown order event type: " + value,
                    exception
            );
        }
    }

    private void requireVersion(int actualVersion, int supportedVersion) {
        if (actualVersion != supportedVersion) {
            throw invalidEvent("Unsupported order event version: " + actualVersion);
        }
    }

    private NonRetryableOrderEventException invalidEvent(String message) {
        return new NonRetryableOrderEventException(message);
    }
}
