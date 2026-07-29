package com.app.notification.messaging;

import com.app.notification.event.EventVersions;
import com.app.notification.event.OrderCreatedEvent;
import com.app.notification.event.OrderEventType;
import com.app.notification.event.OrderFailedEvent;
import com.app.notification.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${app.messaging.topics.order-events}",
            groupId = "${app.messaging.notification.group-id}"
    )
    public void consume(String payload) throws JsonProcessingException {
        String eventType = objectMapper.readTree(payload)
                .path("eventType")
                .asText();

        if (OrderEventType.ORDER_CREATED.name().equals(eventType)) {
            consumeOrderCreated(payload);
            return;
        }
        if (OrderEventType.ORDER_FAILED.name().equals(eventType)) {
            consumeOrderFailed(payload);
            return;
        }
        log.warn("Skip unknown order event type {}", eventType);
    }

    private void consumeOrderCreated(String payload)
            throws JsonProcessingException {
        OrderCreatedEvent event =
                objectMapper.readValue(payload, OrderCreatedEvent.class);
        if (event.eventVersion() != EventVersions.ORDER_CREATED) {
            log.warn(
                    "Skip OrderCreatedEvent {} because version {} is not supported",
                    event.messageId(),
                    event.eventVersion()
            );
            return;
        }
        notificationService.createFor(event);
    }

    private void consumeOrderFailed(String payload)
            throws JsonProcessingException {
        OrderFailedEvent event =
                objectMapper.readValue(payload, OrderFailedEvent.class);
        if (event.eventVersion() != EventVersions.ORDER_FAILED) {
            log.warn(
                    "Skip OrderFailedEvent {} because version {} is not supported",
                    event.messageId(),
                    event.eventVersion()
            );
            return;
        }
        notificationService.replaceWithFailure(event);
    }
}
