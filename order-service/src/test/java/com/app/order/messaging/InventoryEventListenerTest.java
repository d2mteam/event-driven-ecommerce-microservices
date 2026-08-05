package com.app.order.messaging;

import com.app.order.event.EventVersions;
import com.app.order.event.InventoryEventType;
import com.app.order.event.ReservationExpiredEvent;
import com.app.order.model.ReservationExpirationOutcome;
import com.app.order.service.OrderPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryEventListenerTest {

    @Mock
    private OrderPersistenceService persistenceService;

    private ObjectMapper objectMapper;
    private InventoryEventListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        listener = new InventoryEventListener(objectMapper, persistenceService);
    }

    @Test
    void appliesValidReservationExpiration() throws Exception {
        ReservationExpiredEvent event = reservationExpiredEvent();
        when(persistenceService.failExpiredReservation(event))
                .thenReturn(ReservationExpirationOutcome.APPLIED);

        listener.consume(objectMapper.writeValueAsString(event));

        verify(persistenceService).failExpiredReservation(event);
    }

    @Test
    void treatsOrphanReservationAsExpectedNoOp() throws Exception {
        ReservationExpiredEvent event = reservationExpiredEvent();
        when(persistenceService.failExpiredReservation(event))
                .thenReturn(ReservationExpirationOutcome.ORPHAN);

        listener.consume(objectMapper.writeValueAsString(event));

        verify(persistenceService).failExpiredReservation(event);
    }

    @Test
    void rejectsInvariantViolationWithoutRetryingIt() throws Exception {
        ReservationExpiredEvent event = reservationExpiredEvent();
        when(persistenceService.failExpiredReservation(event))
                .thenReturn(ReservationExpirationOutcome.INVARIANT_VIOLATION);

        assertThatThrownBy(
                () -> listener.consume(objectMapper.writeValueAsString(event))
        ).isInstanceOf(NonRetryableOrderEventException.class)
                .hasMessageContaining("conflicts with order state");
    }

    @Test
    void rejectsIncompleteEventBeforeCallingPersistence() {
        String payload = """
                {
                  "eventVersion": 1,
                  "eventType": "RESERVATION_EXPIRED"
                }
                """;

        // Compact constructor của record chặn ngay lúc deserialize, nên lỗi
        // đi ra dưới dạng lỗi JSON. Điều cần giữ là: không retry, không gọi service.
        assertThatThrownBy(() -> listener.consume(payload))
                .isInstanceOf(NonRetryableOrderEventException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("ReservationExpiredEvent is missing required fields");

        verifyNoInteractions(persistenceService);
    }

    @Test
    void leavesInfrastructureFailureForKafkaRetryHandler() throws Exception {
        ReservationExpiredEvent event = reservationExpiredEvent();
        RuntimeException databaseUnavailable =
                new RuntimeException("database unavailable");
        when(persistenceService.failExpiredReservation(event))
                .thenThrow(databaseUnavailable);

        assertThatThrownBy(
                () -> listener.consume(objectMapper.writeValueAsString(event))
        ).isSameAs(databaseUnavailable);
    }

    private ReservationExpiredEvent reservationExpiredEvent() {
        return new ReservationExpiredEvent(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                EventVersions.RESERVATION_EXPIRED,
                InventoryEventType.RESERVATION_EXPIRED,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                42L,
                Instant.parse("2026-08-04T04:00:00Z")
        );
    }
}
