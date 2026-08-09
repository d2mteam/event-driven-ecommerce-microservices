package com.app.paymentgateway.messaging;

import com.app.paymentgateway.event.EventVersions;
import com.app.paymentgateway.event.OrderCancellationRequestedEvent;
import com.app.paymentgateway.event.OrderEventType;
import com.app.paymentgateway.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private PaymentService paymentService;

    private ObjectMapper objectMapper;
    private OrderEventListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        listener = new OrderEventListener(objectMapper, paymentService);
    }

    @Test
    void consumesCancellationRequest() throws Exception {
        OrderCancellationRequestedEvent event =
                new OrderCancellationRequestedEvent(
                        UUID.randomUUID(),
                        EventVersions.ORDER_CANCELLATION_REQUESTED,
                        OrderEventType.ORDER_CANCELLATION_REQUESTED,
                        UUID.randomUUID(),
                        42L,
                        new BigDecimal("199000.00"),
                        Instant.parse("2026-08-04T04:00:00Z")
                );

        listener.consume(objectMapper.writeValueAsString(event));

        verify(paymentService).refund(any(OrderCancellationRequestedEvent.class));
    }
}
