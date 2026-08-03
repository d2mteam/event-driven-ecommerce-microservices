package com.app.order.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.app.order.event.EventVersions;
import com.app.order.event.InventoryEventType;
import com.app.order.event.ReservationExpiredEvent;
import com.app.order.model.ReservationExpirationOutcome;
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
    public void consume(String payload) {
        ReservationExpiredEvent event = readEvent(payload);
        validate(event);

        ReservationExpirationOutcome outcome =
                persistenceService.failExpiredReservation(event);

        switch (outcome) {
            case APPLIED -> log.warn(
                    "Order {} failed because reservation {} expired",
                    event.orderId(),
                    event.reservationId()
            );
            case DUPLICATE -> log.debug(
                    "Reservation expiration event {} was already applied",
                    event.messageId()
            );
            case ORPHAN -> log.info(
                    "Ignore expired orphan reservation {} for order {}",
                    event.reservationId(),
                    event.orderId()
            );
            case INVARIANT_VIOLATION -> throw invalidEvent(
                    "Reservation expiration conflicts with order state: "
                            + event.messageId()
            );
        }
    }

    private ReservationExpiredEvent readEvent(String payload) {
        if (payload == null || payload.isBlank()) {
            throw invalidEvent("Inventory event payload is empty");
        }
        try {
            return objectMapper.readValue(
                    payload,
                    ReservationExpiredEvent.class
            );
        } catch (JsonProcessingException exception) {
            throw new NonRetryableOrderEventException(
                    "Malformed inventory event JSON",
                    exception
            );
        }
    }

    private void validate(ReservationExpiredEvent event) {
        if (event == null) {
            throw invalidEvent("Inventory event must be a JSON object");
        }
        if (event.eventVersion() != EventVersions.RESERVATION_EXPIRED
                || event.eventType()
                != InventoryEventType.RESERVATION_EXPIRED) {
            throw invalidEvent(
                    "Unsupported inventory event version or type"
            );
        }
        if (event.messageId() == null
                || event.orderId() == null
                || event.reservationId() == null
                || event.occurredAt() == null) {
            throw invalidEvent("Inventory event is missing required fields");
        }
    }

    private NonRetryableOrderEventException invalidEvent(String message) {
        return new NonRetryableOrderEventException(message);
    }
}
