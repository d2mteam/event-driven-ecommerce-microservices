package com.app.order.messaging;

import com.app.order.event.EventVersions;
import com.app.order.event.PaymentEventType;
import com.app.order.event.PaymentResultEvent;
import com.app.order.model.PaymentResultOutcome;
import com.app.order.service.OrderPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    @Mock
    private OrderPersistenceService persistenceService;

    private ObjectMapper objectMapper;
    private PaymentEventListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        listener = new PaymentEventListener(objectMapper, persistenceService);
    }

    @Test
    void appliesValidPaymentEvent() throws Exception {
        PaymentResultEvent event = paymentEvent();
        when(persistenceService.applyPaymentResult(event))
                .thenReturn(PaymentResultOutcome.APPLIED);

        listener.consume(objectMapper.writeValueAsString(event));

        verify(persistenceService).applyPaymentResult(event);
    }

    @Test
    void acceptsDuplicateAsSuccessfulConsumption() throws Exception {
        PaymentResultEvent event = paymentEvent();
        when(persistenceService.applyPaymentResult(event))
                .thenReturn(PaymentResultOutcome.DUPLICATE);

        listener.consume(objectMapper.writeValueAsString(event));

        verify(persistenceService).applyPaymentResult(event);
    }

    @Test
    void rejectsInvariantViolationWithoutRetryingIt() throws Exception {
        PaymentResultEvent event = paymentEvent();
        when(persistenceService.applyPaymentResult(event))
                .thenReturn(PaymentResultOutcome.INVARIANT_VIOLATION);

        assertThatThrownBy(
                () -> listener.consume(objectMapper.writeValueAsString(event))
        ).isInstanceOf(NonRetryableOrderEventException.class)
                .hasMessageContaining("conflicts with order state");
    }

    @Test
    void rejectsMalformedJsonBeforeCallingPersistence() {
        assertThatThrownBy(() -> listener.consume("{not-json"))
                .isInstanceOf(NonRetryableOrderEventException.class)
                .hasMessage("Malformed payment event JSON");

        verifyNoInteractions(persistenceService);
    }

    @Test
    void leavesInfrastructureFailureForKafkaRetryHandler() throws Exception {
        PaymentResultEvent event = paymentEvent();
        RuntimeException databaseUnavailable =
                new RuntimeException("database unavailable");
        when(persistenceService.applyPaymentResult(event))
                .thenThrow(databaseUnavailable);

        assertThatThrownBy(
                () -> listener.consume(objectMapper.writeValueAsString(event))
        ).isSameAs(databaseUnavailable);
    }

    private PaymentResultEvent paymentEvent() {
        return new PaymentResultEvent(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                EventVersions.PAYMENT_RESULT,
                PaymentEventType.PAYMENT_SUCCEEDED,
                42L,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                new BigDecimal("199000.00"),
                Instant.parse("2026-08-04T04:00:00Z")
        );
    }
}
