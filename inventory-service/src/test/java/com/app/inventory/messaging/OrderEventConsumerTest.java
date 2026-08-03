package com.app.inventory.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.inventory.service.InventoryReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private InventoryReservationService reservationService;

    private ObjectMapper objectMapper;
    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new OrderEventConsumer(objectMapper, reservationService);
    }

    @Test
    void consumesSupportedOrderConfirmedEvent() throws Exception {
        OrderConfirmedEvent event = confirmedEvent();

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(reservationService)
                .settleConfirmedOrder(any(OrderConfirmedEvent.class));
    }

    @Test
    void consumesSupportedOrderFailedEvent() throws Exception {
        OrderFailedEvent event = failedEvent();

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(reservationService).releaseFailedOrder(event);
    }

    @Test
    void rejectsMalformedJsonWithoutCallingTheService() {
        assertThatThrownBy(() -> consumer.consume("{not-json"))
                .isInstanceOf(NonRetryableOrderEventException.class)
                .hasMessage("Malformed order event JSON");

        verifyNoInteractions(reservationService);
    }

    @Test
    void rejectsUnknownEventTypeWithoutCallingTheService() {
        String payload = """
                {
                  "eventType": "ORDER_DELETED"
                }
                """;

        assertThatThrownBy(() -> consumer.consume(payload))
                .isInstanceOf(NonRetryableOrderEventException.class)
                .hasMessage("Unknown order event type: ORDER_DELETED");

        verifyNoInteractions(reservationService);
    }

    @Test
    void rejectsMissingRequiredFieldWithoutCallingTheService() {
        ObjectNode eventJson = objectMapper.valueToTree(confirmedEvent());
        eventJson.remove("orderId");

        assertThatThrownBy(() -> consumer.consume(eventJson.toString()))
                .isInstanceOf(NonRetryableOrderEventException.class)
                .hasMessage("Missing required field: orderId");

        verifyNoInteractions(reservationService);
    }

    @Test
    void rejectsUnsupportedVersionWithoutCallingTheService() {
        ObjectNode eventJson = objectMapper.valueToTree(confirmedEvent());
        eventJson.put("eventVersion", EventVersions.ORDER_CONFIRMED + 1);

        assertThatThrownBy(() -> consumer.consume(eventJson.toString()))
                .isInstanceOf(NonRetryableOrderEventException.class)
                .hasMessage("Unsupported OrderConfirmedEvent version: 2");

        verifyNoInteractions(reservationService);
    }

    @Test
    void letsInfrastructureFailureReachKafkaErrorHandler() throws Exception {
        RuntimeException databaseUnavailable =
                new RuntimeException("database unavailable");
        doThrow(databaseUnavailable)
                .when(reservationService)
                .settleConfirmedOrder(any(OrderConfirmedEvent.class));

        assertThatThrownBy(
                () -> consumer.consume(
                        objectMapper.writeValueAsString(confirmedEvent())
                )
        ).isSameAs(databaseUnavailable);
    }

    private OrderConfirmedEvent confirmedEvent() {
        return new OrderConfirmedEvent(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                EventVersions.ORDER_CONFIRMED,
                OrderEventType.ORDER_CONFIRMED,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                42L,
                new BigDecimal("199000.00"),
                List.of(new OrderItem(
                        7L,
                        "Mechanical Keyboard",
                        new BigDecimal("199000.00"),
                        1
                )),
                Instant.parse("2026-08-04T04:00:00Z")
        );
    }

    private OrderFailedEvent failedEvent() {
        return new OrderFailedEvent(
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                EventVersions.ORDER_FAILED,
                OrderEventType.ORDER_FAILED,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                42L,
                "PAYMENT_FAILED",
                Instant.parse("2026-08-04T04:00:00Z")
        );
    }
}
