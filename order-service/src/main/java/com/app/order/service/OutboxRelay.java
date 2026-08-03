package com.app.order.service;

import com.app.order.config.OutboxRelayProperties;
import com.app.order.entity.OutboxMessage;
import com.app.order.model.OutboxStatus;
import com.app.order.repository.OutboxMessageRepository;
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
public class OutboxRelay {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final OutboxMessageRepository outboxMessageRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxRelayProperties properties;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(
            initialDelayString = "${app.outbox.initial-delay}",
            fixedDelayString = "${app.outbox.fixed-delay}"
    )
    public void publishUnpublishedMessages() {
        List<OutboxMessage> messages;
        try {
            messages = claimMessages();
        } catch (RuntimeException exception) {
            log.error("Order outbox claim failed", exception);
            return;
        }

        for (OutboxMessage message : messages) {
            if (!publishAndFinalize(message)) {
                break;
            }
        }
    }

    private List<OutboxMessage> claimMessages() {
        List<OutboxMessage> messages = transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            Instant lockedUntil = now.plus(properties.getLeaseDuration());
            String lockToken = UUID.randomUUID().toString();

            List<OutboxMessage> claimable =
                    outboxMessageRepository.findClaimableForUpdate(
                            now,
                            properties.getBatchSize()
                    );
            claimable.forEach(message -> {
                message.claim(lockToken, lockedUntil);
                log.debug(
                        "Claimed order outbox message id={}, attempt={}, token={}",
                        message.getId(),
                        message.getAttemptCount(),
                        lockToken
                );
            });
            return List.copyOf(claimable);
        });

        return messages == null ? List.of() : messages;
    }

    private boolean publishAndFinalize(OutboxMessage message) {
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

    private void markPublished(OutboxMessage message) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                int updated = outboxMessageRepository.markPublished(
                        message.getId(),
                        message.getLockToken(),
                        OutboxStatus.PROCESSING,
                        OutboxStatus.PUBLISHED,
                        Instant.now()
                );
                requireFinalized(message, updated, OutboxStatus.PUBLISHED);
            });
            log.debug(
                    "Published order outbox message id={}, attempt={}, token={}",
                    message.getId(),
                    message.getAttemptCount(),
                    message.getLockToken()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Kafka accepted order outbox message id={} but DB finalize failed; "
                            + "the lease will allow redelivery",
                    message.getId(),
                    exception
            );
        }
    }

    private void scheduleRetry(OutboxMessage message, Exception exception) {
        Instant nextAttemptAt = Instant.now().plus(backoffFor(message));
        String error = errorSummary(exception);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                int updated = outboxMessageRepository.scheduleRetry(
                        message.getId(),
                        message.getLockToken(),
                        OutboxStatus.PROCESSING,
                        OutboxStatus.PENDING,
                        nextAttemptAt,
                        error
                );
                requireFinalized(message, updated, OutboxStatus.PENDING);
            });
        } catch (RuntimeException finalizeException) {
            log.error(
                    "Cannot schedule retry for order outbox message id={}; "
                            + "the lease will allow redelivery",
                    message.getId(),
                    finalizeException
            );
            return;
        }

        if (message.getAttemptCount() >= properties.getAlertAfterAttempts()) {
            log.error(
                    "Order outbox message id={} still failing after attempt={}; "
                            + "nextAttemptAt={}, error={}",
                    message.getId(),
                    message.getAttemptCount(),
                    nextAttemptAt,
                    error
            );
        } else {
            log.warn(
                    "Order outbox message id={} publish failed at attempt={}; "
                            + "nextAttemptAt={}, error={}",
                    message.getId(),
                    message.getAttemptCount(),
                    nextAttemptAt,
                    error
            );
        }
    }

    private void markFailed(OutboxMessage message, Exception exception) {
        String error = errorSummary(exception);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                int updated = outboxMessageRepository.markFailed(
                        message.getId(),
                        message.getLockToken(),
                        OutboxStatus.PROCESSING,
                        OutboxStatus.FAILED,
                        error
                );
                requireFinalized(message, updated, OutboxStatus.FAILED);
            });
            log.error(
                    "Order outbox message id={} permanently failed at attempt={}; error={}",
                    message.getId(),
                    message.getAttemptCount(),
                    error
            );
        } catch (RuntimeException finalizeException) {
            log.error(
                    "Cannot mark order outbox message id={} as FAILED; "
                            + "the lease will allow redelivery",
                    message.getId(),
                    finalizeException
            );
        }
    }

    private Duration backoffFor(OutboxMessage message) {
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
            OutboxMessage message,
            int updated,
            OutboxStatus targetStatus
    ) {
        if (updated != 1) {
            throw new IllegalStateException(
                    "Outbox message %d was not finalized as %s"
                            .formatted(message.getId(), targetStatus)
            );
        }
    }
}
