package com.app.order.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.app.order.event.EventVersions;
import com.app.order.event.InventoryEventType;
import com.app.order.event.ReservationExpiredEvent;
import com.app.order.service.OrderPersistenceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventListener {

    private final ObjectMapper objectMapper;
    private final OrderPersistenceService persistenceService;

    @KafkaListener(
            topics = "${app.kafka.topics.inventory-events}",
            groupId = "${app.kafka.consumer-group-id}"
    )
    public void consume(String payload) throws JsonProcessingException {
        ReservationExpiredEvent event =
                objectMapper.readValue(payload, ReservationExpiredEvent.class);

        if (event.eventVersion() != EventVersions.RESERVATION_EXPIRED
                || event.eventType() != InventoryEventType.RESERVATION_EXPIRED) {
            log.warn(
                    "Skip unsupported inventory event {}",
                    event.messageId()
            );
            return;
        }

        boolean orderFailed = persistenceService.failExpiredReservation(event);
        if (orderFailed) {
            log.warn(
                    "Order {} failed because reservation {} expired",
                    event.orderId(),
                    event.reservationId()
            );
        }
    }
}
