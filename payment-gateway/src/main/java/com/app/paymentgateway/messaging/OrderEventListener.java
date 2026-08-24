package com.app.paymentgateway.messaging;

import com.app.paymentgateway.event.EventVersions;
import com.app.paymentgateway.event.OrderCancellationRequestedEvent;
import com.app.paymentgateway.event.OrderEventType;
import com.app.paymentgateway.service.PaymentRefundService;
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
    private final PaymentRefundService paymentRefundService;

    @KafkaListener(
            topics = "${app.messaging.topics.order-events}",
            groupId = "${app.messaging.consumer-group-id}"
    )
    public void consume(String payload) {
        JsonNode eventJson = readTree(payload);
        OrderEventType eventType = readEventType(eventJson);

        if (eventType != OrderEventType.ORDER_CANCELLATION_REQUESTED) {
            return;
        }

        OrderCancellationRequestedEvent event = readEvent(eventJson);
        if (event.eventVersion()
                != EventVersions.ORDER_CANCELLATION_REQUESTED) {
            throw invalidEvent(
                    "Unsupported OrderCancellationRequestedEvent version: "
                            + event.eventVersion()
            );
        }
        paymentRefundService.refund(event);
    }

    private JsonNode readTree(String payload) {
        if (payload == null || payload.isBlank()) {
            throw invalidEvent("Order event payload is empty");
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

    private OrderEventType readEventType(JsonNode eventJson) {
        if (eventJson == null || !eventJson.isObject()
                || !eventJson.hasNonNull("eventType")) {
            throw invalidEvent("Missing required field: eventType");
        }
        String value = eventJson.get("eventType").asText();
        try {
            return OrderEventType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalidEvent("Unknown order event type: " + value);
        }
    }

    private OrderCancellationRequestedEvent readEvent(JsonNode eventJson) {
        try {
            return objectMapper.treeToValue(
                    eventJson,
                    OrderCancellationRequestedEvent.class
            );
        } catch (JsonProcessingException exception) {
            throw new NonRetryableOrderEventException(
                    "Invalid OrderCancellationRequestedEvent",
                    exception
            );
        }
    }

    private NonRetryableOrderEventException invalidEvent(String message) {
        return new NonRetryableOrderEventException(message);
    }
}
