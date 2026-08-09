package com.app.paymentgateway.service;

import com.app.paymentgateway.config.PaymentMessagingProperties;
import com.app.paymentgateway.config.PaymentProperties;
import com.app.paymentgateway.entity.Payment;
import com.app.paymentgateway.entity.PaymentOutboxMessage;
import com.app.paymentgateway.event.OrderCancellationRequestedEvent;
import com.app.paymentgateway.event.OrderEventType;
import com.app.paymentgateway.model.PaymentStatus;
import com.app.paymentgateway.repository.PaymentOutboxMessageRepository;
import com.app.paymentgateway.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRefundTest {

    private static final UUID ORDER_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentOutboxMessageRepository outboxRepository;

    @Test
    void refundsSucceededPaymentAndWritesResultEvent() {
        Payment payment = succeededPayment();
        when(paymentRepository.findByOrderIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(payment));

        service().refund(cancellationRequested());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        ArgumentCaptor<PaymentOutboxMessage> outbox =
                ArgumentCaptor.forClass(PaymentOutboxMessage.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getType()).isEqualTo("PAYMENT_REFUNDED");
    }

    @Test
    void repeatedRefundDoesNotWriteAnotherEvent() {
        Payment payment = succeededPayment();
        payment.refund();
        when(paymentRepository.findByOrderIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(payment));

        service().refund(cancellationRequested());

        verify(outboxRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private PaymentService service() {
        PaymentMessagingProperties messaging = new PaymentMessagingProperties();
        messaging.getTopics().setPaymentEvents("payment.events");
        return new PaymentService(
                paymentRepository,
                outboxRepository,
                new PaymentProperties(
                        Duration.ofMinutes(15),
                        Duration.ofSeconds(30),
                        "http://localhost:8080",
                        50,
                        10
                ),
                messaging,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    private Payment succeededPayment() {
        Instant createdAt = Instant.parse("2026-08-04T03:00:00Z");
        Payment payment = Payment.pending(
                ORDER_ID,
                new BigDecimal("199000.00"),
                createdAt,
                createdAt.plusSeconds(900)
        );
        payment.complete(
                PaymentStatus.SUCCEEDED,
                Instant.parse("2026-08-04T03:05:00Z")
        );
        return payment;
    }

    private OrderCancellationRequestedEvent cancellationRequested() {
        return new OrderCancellationRequestedEvent(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                1,
                OrderEventType.ORDER_CANCELLATION_REQUESTED,
                ORDER_ID,
                42L,
                new BigDecimal("199000.00"),
                Instant.parse("2026-08-04T04:00:00Z")
        );
    }
}
