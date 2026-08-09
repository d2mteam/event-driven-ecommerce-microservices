package com.app.notification.messaging;

import com.app.notification.event.EventVersions;
import com.app.notification.event.OrderConfirmedEvent;
import com.app.notification.event.OrderEventType;
import com.app.notification.event.OrderFailedEvent;
import com.app.notification.event.OrderItem;
import com.app.notification.event.OrderCancelledEvent;
import com.app.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private NotificationService notificationService;

    private ObjectMapper objectMapper;
    private OrderEventListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        listener = new OrderEventListener(objectMapper, notificationService);
    }

    @Test
    void consumesSupportedOrderConfirmedEvent() throws Exception {
        OrderConfirmedEvent event = confirmedEvent();

        listener.consume(objectMapper.writeValueAsString(event));

        verify(notificationService).createSuccessFor(any(OrderConfirmedEvent.class));
    }

    @Test
    void consumesSupportedOrderFailedEvent() throws Exception {
        OrderFailedEvent event = new OrderFailedEvent(
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                EventVersions.ORDER_FAILED,
                OrderEventType.ORDER_FAILED,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                42L,
                "PAYMENT_FAILED",
                Instant.parse("2026-08-04T04:00:00Z")
        );

        listener.consume(objectMapper.writeValueAsString(event));

        verify(notificationService).replaceWithFailure(event);
    }

    @Test
    void consumesSupportedOrderCancelledEvent() throws Exception {
        OrderCancelledEvent event = new OrderCancelledEvent(
                UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                EventVersions.ORDER_CANCELLED,
                OrderEventType.ORDER_CANCELLED,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                42L,
                Instant.parse("2026-08-04T05:00:00Z")
        );

        listener.consume(objectMapper.writeValueAsString(event));

        verify(notificationService).replaceWithCancellation(event);
    }

    @Test
    void rejectsMalformedJsonWithoutCallingTheService() {
        assertThatThrownBy(() -> listener.consume("{not-json"))
                .isInstanceOf(NonRetryableOrderEventException.class)
                .hasMessage("Malformed order event JSON");

        verifyNoInteractions(notificationService);
    }

    @Test
    void rejectsUnknownEventTypeWithoutCallingTheService() {
        String payload = """
                {
                  "eventType": "ORDER_DELETED"
                }
                """;

        assertThatThrownBy(() -> listener.consume(payload))
                .isInstanceOf(NonRetryableOrderEventException.class)
                .hasMessage("Unknown order event type: ORDER_DELETED");

        verifyNoInteractions(notificationService);
    }

    @Test
    void rejectsMissingRequiredFieldWithoutCallingTheService() throws Exception {
        ObjectNode eventJson = objectMapper.valueToTree(confirmedEvent());
        eventJson.remove("orderId");

        // Compact constructor của record chặn ngay lúc deserialize, nên lỗi
        // đi ra dưới dạng lỗi JSON. Điều cần giữ là: không retry, không gọi service.
        assertThatThrownBy(() -> listener.consume(eventJson.toString()))
                .isInstanceOf(NonRetryableOrderEventException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("OrderConfirmedEvent is missing required fields");

        verifyNoInteractions(notificationService);
    }

    @Test
    void rejectsUnsupportedVersionWithoutCallingTheService() throws Exception {
        OrderConfirmedEvent event = confirmedEvent();
        ObjectNode eventJson = objectMapper.valueToTree(event);
        eventJson.put("eventVersion", EventVersions.ORDER_CONFIRMED + 1);

        assertThatThrownBy(() -> listener.consume(eventJson.toString()))
                .isInstanceOf(NonRetryableOrderEventException.class)
                .hasMessage("Unsupported OrderConfirmedEvent version: 2");

        verifyNoInteractions(notificationService);
    }

    @Test
    void letsInfrastructureFailureReachKafkaErrorHandler() throws Exception {
        OrderConfirmedEvent event = confirmedEvent();
        RuntimeException databaseUnavailable =
                new RuntimeException("database unavailable");
        doThrow(databaseUnavailable)
                .when(notificationService)
                .createSuccessFor(any(OrderConfirmedEvent.class));

        assertThatThrownBy(
                () -> listener.consume(objectMapper.writeValueAsString(event))
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
}
