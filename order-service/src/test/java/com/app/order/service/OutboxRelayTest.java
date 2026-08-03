package com.app.order.service;

import com.app.order.config.OutboxRelayProperties;
import com.app.order.entity.OutboxMessage;
import com.app.order.model.OutboxStatus;
import com.app.order.repository.OutboxMessageRepository;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final long MESSAGE_ID = 42L;

    @Mock
    private OutboxMessageRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any()))
                .thenAnswer(ignored -> mock(TransactionStatus.class));

        OutboxRelayProperties properties = new OutboxRelayProperties();
        properties.setBatchSize(50);
        properties.setSendTimeout(Duration.ofSeconds(1));
        properties.setLeaseDuration(Duration.ofMinutes(10));
        properties.setRetryInitialDelay(Duration.ofSeconds(1));
        properties.setRetryMultiplier(2.0);
        properties.setRetryMaxDelay(Duration.ofSeconds(60));
        properties.setAlertAfterAttempts(10);

        relay = new OutboxRelay(
                repository,
                kafkaTemplate,
                properties,
                new TransactionTemplate(transactionManager)
        );
    }

    @Test
    void successfulSendMarksClaimedMessageAsPublished() {
        OutboxMessage message = claimedByRelay();
        when(kafkaTemplate.send(message.getTopic(), message.getKey(), message.getPayload()))
                .thenReturn(successfulSend());
        when(repository.markPublished(
                eq(MESSAGE_ID),
                any(),
                eq(OutboxStatus.PROCESSING),
                eq(OutboxStatus.PUBLISHED),
                any()
        )).thenReturn(1);

        relay.publishUnpublishedMessages();

        verify(repository).markPublished(
                eq(MESSAGE_ID),
                eq(message.getLockToken()),
                eq(OutboxStatus.PROCESSING),
                eq(OutboxStatus.PUBLISHED),
                any()
        );
        verify(repository, never()).scheduleRetry(any(), any(), any(), any(), any(), any());
        verify(repository, never()).markFailed(any(), any(), any(), any(), any());
    }

    @Test
    void timeoutSchedulesRetryWithoutMarkingMessageAsFailed() {
        OutboxMessage message = claimedByRelay();
        when(kafkaTemplate.send(message.getTopic(), message.getKey(), message.getPayload()))
                .thenReturn(failedSend(new TimeoutException("broker unavailable")));
        when(repository.scheduleRetry(
                eq(MESSAGE_ID),
                any(),
                eq(OutboxStatus.PROCESSING),
                eq(OutboxStatus.PENDING),
                any(),
                any()
        )).thenReturn(1);
        Instant beforePublish = Instant.now();

        relay.publishUnpublishedMessages();

        ArgumentCaptor<Instant> nextAttemptAt = ArgumentCaptor.forClass(Instant.class);
        verify(repository).scheduleRetry(
                eq(MESSAGE_ID),
                eq(message.getLockToken()),
                eq(OutboxStatus.PROCESSING),
                eq(OutboxStatus.PENDING),
                nextAttemptAt.capture(),
                eq("TimeoutException: broker unavailable")
        );
        assertThat(nextAttemptAt.getValue())
                .isAfterOrEqualTo(beforePublish.plusSeconds(1));
        verify(repository, never()).markFailed(any(), any(), any(), any(), any());
    }

    @Test
    void serializationFailureMarksMessageAsFailedWithoutRetry() {
        OutboxMessage message = claimedByRelay();
        when(kafkaTemplate.send(message.getTopic(), message.getKey(), message.getPayload()))
                .thenReturn(failedSend(new SerializationException("invalid payload")));
        when(repository.markFailed(
                eq(MESSAGE_ID),
                any(),
                eq(OutboxStatus.PROCESSING),
                eq(OutboxStatus.FAILED),
                any()
        )).thenReturn(1);

        relay.publishUnpublishedMessages();

        verify(repository).markFailed(
                eq(MESSAGE_ID),
                eq(message.getLockToken()),
                eq(OutboxStatus.PROCESSING),
                eq(OutboxStatus.FAILED),
                eq("SerializationException: invalid payload")
        );
        verify(repository, never()).scheduleRetry(any(), any(), any(), any(), any(), any());
    }

    @Test
    void kafkaAckWithLostFinalizeKeepsLeaseForLaterRecovery() {
        OutboxMessage message = claimedByRelay();
        when(kafkaTemplate.send(message.getTopic(), message.getKey(), message.getPayload()))
                .thenReturn(successfulSend());
        when(repository.markPublished(
                eq(MESSAGE_ID),
                any(),
                eq(OutboxStatus.PROCESSING),
                eq(OutboxStatus.PUBLISHED),
                any()
        )).thenReturn(0);

        assertThatCode(relay::publishUnpublishedMessages)
                .doesNotThrowAnyException();

        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(message.getLockToken()).isNotBlank();
        assertThat(message.getLockedUntil()).isAfter(Instant.now());
        verify(repository, never()).scheduleRetry(any(), any(), any(), any(), any(), any());
        verify(repository, never()).markFailed(any(), any(), any(), any(), any());
    }

    private OutboxMessage claimedByRelay() {
        OutboxMessage message = OutboxMessage.builder()
                .id(MESSAGE_ID)
                .messageId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .topic("order.events")
                .key("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
                .type("OrderConfirmedEvent")
                .payload("{\"orderId\":\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\"}")
                .createdAt(Instant.parse("2026-08-04T00:00:00Z"))
                .status(OutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(Instant.parse("2026-08-04T00:00:00Z"))
                .build();
        when(repository.findClaimableForUpdate(any(), eq(50)))
                .thenReturn(List.of(message));
        return message;
    }

    private CompletableFuture<SendResult<String, String>> successfulSend() {
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<SendResult<String, String>> failedSend(
            RuntimeException exception
    ) {
        return CompletableFuture.failedFuture(exception);
    }
}
