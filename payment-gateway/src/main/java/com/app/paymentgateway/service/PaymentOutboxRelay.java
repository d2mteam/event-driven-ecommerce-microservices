package com.app.paymentgateway.service;

import com.app.paymentgateway.config.PaymentOutboxProperties;
import com.app.paymentgateway.entity.PaymentOutboxMessage;
import com.app.paymentgateway.model.PaymentOutboxStatus;
import com.app.paymentgateway.repository.PaymentOutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOutboxRelay {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final PaymentOutboxMessageRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PaymentOutboxProperties properties;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(
            initialDelayString = "${app.outbox.initial-delay}",
            fixedDelayString = "${app.outbox.fixed-delay}"
    )
    public void publishUnpublishedMessages() {
        List<PaymentOutboxMessage> messages;
        try {
            messages = claimMessages();
        } catch (RuntimeException exception) {
            log.error("Payment outbox claim failed", exception);
            return;
        }

        for (PaymentOutboxMessage message : messages) {
            if (!publishAndFinalize(message)) {
                break;
            }
        }
    }

    private List<PaymentOutboxMessage> claimMessages() {
        List<PaymentOutboxMessage> messages = transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            String lockToken = UUID.randomUUID().toString();
            Instant lockedUntil = now.plus(properties.getLeaseDuration());

            List<PaymentOutboxMessage> claimable =
                    outboxRepository.findClaimableForUpdate(
                            now,
                            properties.getBatchSize()
                    );
            claimable.forEach(message -> {
                message.claim(lockToken, lockedUntil);
                log.debug(
                        "Claimed payment outbox message id={}, attempt={}, token={}",
                        message.getId(),
                        message.getAttemptCount(),
                        lockToken
                );
            });
            return List.copyOf(claimable);
        });

        return messages == null ? List.of() : messages;
    }

    private boolean publishAndFinalize(PaymentOutboxMessage message) {
        try {
            kafkaTemplate.send(
                            message.getTopic(),
                            message.getKey(),
                            message.getPayload()
                    )
                    .get(
                            properties.getSendTimeout().toMillis(),
                            TimeUnit.MILLISECONDS
                    );
            markPublished(message);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            scheduleRetry(message, exception);
            return false;
        } catch (Exception exception) {
            if (isPermanentProducerFailure(exception)) {
                markFailed(message, exception);
            } else {
                scheduleRetry(message, exception);
            }
            return true;
        }
    }

    private void markPublished(PaymentOutboxMessage message) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                int updated = outboxRepository.markPublished(
                        message.getId(),
                        message.getLockToken(),
                        PaymentOutboxStatus.PROCESSING,
                        PaymentOutboxStatus.PUBLISHED,
                        Instant.now()
                );
                requireFinalized(message, updated, PaymentOutboxStatus.PUBLISHED);
            });
            log.debug(
                    "Published payment outbox message id={}, attempt={}, token={}",
                    message.getId(),
                    message.getAttemptCount(),
                    message.getLockToken()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Kafka accepted payment outbox message id={} but DB finalize failed; "
                            + "the lease will allow redelivery",
                    message.getId(),
                    exception
            );
        }
    }

    private void scheduleRetry(
            PaymentOutboxMessage message,
            Exception exception
    ) {
        Instant nextAttemptAt = Instant.now().plus(backoffFor(message));
        String error = errorSummary(exception);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                int updated = outboxRepository.scheduleRetry(
                        message.getId(),
                        message.getLockToken(),
                        PaymentOutboxStatus.PROCESSING,
                        PaymentOutboxStatus.PENDING,
                        nextAttemptAt,
                        error
                );
                requireFinalized(message, updated, PaymentOutboxStatus.PENDING);
            });
        } catch (RuntimeException finalizeException) {
            log.error(
                    "Cannot schedule retry for payment outbox message id={}; "
                            + "the lease will allow redelivery",
                    message.getId(),
                    finalizeException
            );
            return;
        }

        if (message.getAttemptCount() >= properties.getAlertAfterAttempts()) {
            log.error(
                    "Payment outbox message id={} still failing after attempt={}; "
                            + "nextAttemptAt={}, error={}",
                    message.getId(),
                    message.getAttemptCount(),
                    nextAttemptAt,
                    error
            );
        } else {
            log.warn(
                    "Payment outbox message id={} publish failed at attempt={}; "
                            + "nextAttemptAt={}, error={}",
                    message.getId(),
                    message.getAttemptCount(),
                    nextAttemptAt,
                    error
            );
        }
    }

    private void markFailed(
            PaymentOutboxMessage message,
            Exception exception
    ) {
        String error = errorSummary(exception);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                int updated = outboxRepository.markFailed(
                        message.getId(),
                        message.getLockToken(),
                        PaymentOutboxStatus.PROCESSING,
                        PaymentOutboxStatus.FAILED,
                        error
                );
                requireFinalized(message, updated, PaymentOutboxStatus.FAILED);
            });
            log.error(
                    "Payment outbox message id={} permanently failed at attempt={}; error={}",
                    message.getId(),
                    message.getAttemptCount(),
                    error
            );
        } catch (RuntimeException finalizeException) {
            log.error(
                    "Cannot mark payment outbox message id={} as FAILED; "
                            + "the lease will allow redelivery",
                    message.getId(),
                    finalizeException
            );
        }
    }

    private Duration backoffFor(PaymentOutboxMessage message) {
        double multiplier = Math.pow(
                properties.getRetryMultiplier(),
                Math.max(0, message.getAttemptCount() - 1)
        );
        double calculatedMillis =
                properties.getRetryInitialDelay().toMillis() * multiplier;
        long delayMillis = (long) Math.min(
                calculatedMillis,
                properties.getRetryMaxDelay().toMillis()
        );
        return Duration.ofMillis(delayMillis);
    }

    private boolean isPermanentProducerFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SerializationException
                    || current instanceof RecordTooLargeException
                    || current instanceof InvalidTopicException
                    || current instanceof AuthorizationException
                    || current instanceof AuthenticationException
                    || current instanceof ConfigException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String errorSummary(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String message = root.getMessage();
        String summary = root.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return summary.length() <= MAX_ERROR_LENGTH
                ? summary
                : summary.substring(0, MAX_ERROR_LENGTH);
    }

    private void requireFinalized(
            PaymentOutboxMessage message,
            int updated,
            PaymentOutboxStatus targetStatus
    ) {
        if (updated != 1) {
            throw new IllegalStateException(
                    "Payment outbox message %d was not finalized as %s"
                            .formatted(message.getId(), targetStatus)
            );
        }
    }
}
