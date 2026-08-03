package com.app.notification.messaging;

import com.app.notification.event.EventVersions;
import com.app.notification.event.OrderConfirmedEvent;
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

        if (OrderEventType.ORDER_CONFIRMED.name().equals(eventType)) {
            consumeOrderConfirmed(payload);
            return;
        }
        if (OrderEventType.ORDER_FAILED.name().equals(eventType)) {
            consumeOrderFailed(payload);
            return;
        }
        log.warn("Skip unknown order event type {}", eventType);
    }

    private void consumeOrderConfirmed(String payload)
            throws JsonProcessingException {
        OrderConfirmedEvent event =
                objectMapper.readValue(payload, OrderConfirmedEvent.class);
        if (event.eventVersion() != EventVersions.ORDER_CONFIRMED) {
            log.warn(
                    "Skip OrderConfirmedEvent {} because version {} is not supported",
                    event.messageId(),
                    event.eventVersion()
            );
            return;
        }
        notificationService.createSuccessFor(event);
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
