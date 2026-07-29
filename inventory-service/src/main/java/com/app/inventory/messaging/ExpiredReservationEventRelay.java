package com.app.inventory.messaging;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.app.inventory.config.InventoryMessagingProperties;
import com.app.inventory.config.InventoryReservationProperties;
import com.app.inventory.entity.InventoryReservation;
import com.app.inventory.entity.ReservationStatus;
import com.app.inventory.repository.InventoryReservationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredReservationEventRelay {

    private final InventoryReservationRepository reservationRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final InventoryMessagingProperties messagingProperties;
    private final InventoryReservationProperties reservationProperties;

    @Scheduled(fixedDelayString = "${app.inventory.reservation.event-relay-delay}")
    @Transactional
    public void publishExpiredReservations() {
        List<InventoryReservation> reservations =
                reservationRepository
                        .findByStatusAndExpirationEventIdIsNotNullAndExpirationEventPublishedAtIsNullOrderByIdAsc(
                                ReservationStatus.EXPIRED,
                                PageRequest.of(
                                        0,
                                        reservationProperties.eventRelayBatchSize()
                                )
                        );

        for (InventoryReservation reservation : reservations) {
            if (!publish(reservation)) {
                break;
            }
            reservation.markExpirationEventPublished(Instant.now());
        }
    }

    private boolean publish(InventoryReservation reservation) {
        ReservationExpiredEvent event = ReservationExpiredEvent.from(
                reservation.getExpirationEventId(),
                reservation.getOrderId(),
                reservation.getId(),
                reservation.getExpiresAt()
        );

        try {
            kafkaTemplate.send(
                            messagingProperties.inventoryEvents(),
                            event.orderId().toString(),
                            objectMapper.writeValueAsString(event)
                    )
                    .get(
                            reservationProperties.eventSendTimeout().toMillis(),
                            TimeUnit.MILLISECONDS
                    );
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Cannot serialize ReservationExpiredEvent",
                    exception
            );
        } catch (Exception exception) {
            log.warn(
                    "Cannot publish expiration event for reservation {}",
                    reservation.getId(),
                    exception
            );
            return false;
        }
    }
}
