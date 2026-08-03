package com.app.paymentgateway.service;

import com.app.paymentgateway.config.PaymentMessagingProperties;
import com.app.paymentgateway.config.PaymentProperties;
import com.app.paymentgateway.entity.Payment;
import com.app.paymentgateway.entity.PaymentOutboxMessage;
import com.app.paymentgateway.model.PaymentStatus;
import com.app.paymentgateway.repository.PaymentOutboxMessageRepository;
import com.app.paymentgateway.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceExpirationTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentOutboxMessageRepository outboxRepository;

    @Test
    void expiresOnlyTheLockedBatchAndCreatesOneOutboxMessagePerPayment() {
        Instant cutoff = Instant.parse("2026-08-04T03:00:00Z");
        Payment first = pendingPayment(cutoff.minusSeconds(20));
        Payment second = pendingPayment(cutoff.minusSeconds(10));
        PaymentMessagingProperties messagingProperties = messagingProperties();
        PaymentService paymentService = new PaymentService(
                paymentRepository,
                outboxRepository,
                new PaymentProperties(
                        Duration.ofMinutes(15),
                        Duration.ofSeconds(30),
                        "http://localhost:8080",
                        2,
                        3
                ),
                messagingProperties,
                new ObjectMapper().findAndRegisterModules()
        );
        when(paymentRepository.findExpiredForUpdate(
                PaymentStatus.PENDING.name(),
                cutoff,
                2
        )).thenReturn(List.of(first, second));

        int expired = paymentService.expirePendingPaymentsBatch(cutoff, 2);

        assertThat(expired).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(second.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(outboxRepository, times(2))
                .save(any(PaymentOutboxMessage.class));
    }

    private Payment pendingPayment(Instant expiresAt) {
        return Payment.pending(
                UUID.randomUUID(),
                BigDecimal.valueOf(100_000),
                expiresAt.minusSeconds(900),
                expiresAt
        );
    }

    private PaymentMessagingProperties messagingProperties() {
        PaymentMessagingProperties properties = new PaymentMessagingProperties();
        PaymentMessagingProperties.Topics topics =
                new PaymentMessagingProperties.Topics();
        topics.setPaymentEvents("payment.events");
        properties.setTopics(topics);
        return properties;
    }
}
