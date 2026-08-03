package com.app.inventory.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.app.inventory.config.InventoryOutboxProperties;
import com.app.inventory.entity.InventoryOutboxMessage;
import com.app.inventory.entity.InventoryOutboxStatus;
import com.app.inventory.repository.InventoryOutboxMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredReservationEventRelay {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final InventoryOutboxMessageRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final InventoryOutboxProperties properties;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(
            initialDelayString = "${app.inventory.outbox.initial-delay}",
            fixedDelayString = "${app.inventory.outbox.fixed-delay}"
    )
    public void publishExpiredReservations() {
        List<InventoryOutboxMessage> messages;
        try {
            messages = claimMessages();
        } catch (RuntimeException exception) {
            log.error("Inventory outbox claim failed", exception);
            return;
        }

        for (InventoryOutboxMessage message : messages) {
            if (!publishAndFinalize(message)) {
                break;
            }
        }
    }

    private List<InventoryOutboxMessage> claimMessages() {
        List<InventoryOutboxMessage> messages = transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            Instant lockedUntil = now.plus(properties.getLeaseDuration());
            String lockToken = UUID.randomUUID().toString();

            List<InventoryOutboxMessage> claimable =
                    outboxRepository.findClaimableForUpdate(
                            now,
                            properties.getBatchSize()
                    );
            for (InventoryOutboxMessage message : claimable) {
                message.claim(lockToken, lockedUntil);
                log.debug(
                        "Claimed inventory outbox message id={}, attempt={}, token={}",
                        message.getId(),
                        message.getAttemptCount(),
                        lockToken
                );
            }
            return List.copyOf(claimable);
        });

        return messages == null ? List.of() : messages;
    }

    private boolean publishAndFinalize(InventoryOutboxMessage message) {
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

    private void markPublished(InventoryOutboxMessage message) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                int updated = outboxRepository.markPublished(
                        message.getId(),
                        message.getLockToken(),
                        InventoryOutboxStatus.PROCESSING,
                        InventoryOutboxStatus.PUBLISHED,
                        Instant.now()
                );
                requireFinalized(message, updated, InventoryOutboxStatus.PUBLISHED);
            });
            log.debug(
                    "Published inventory outbox message id={}, attempt={}, token={}",
                    message.getId(),
                    message.getAttemptCount(),
                    message.getLockToken()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Kafka accepted inventory outbox message id={} but DB finalize failed; "
                            + "the lease will allow redelivery",
                    message.getId(),
                    exception
            );
        }
    }

    private void scheduleRetry(
            InventoryOutboxMessage message,
            Exception exception
    ) {
        Instant nextAttemptAt = Instant.now().plus(backoffFor(message));
        String error = errorSummary(exception);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                int updated = outboxRepository.scheduleRetry(
                        message.getId(),
                        message.getLockToken(),
                        InventoryOutboxStatus.PROCESSING,
                        InventoryOutboxStatus.PENDING,
                        nextAttemptAt,
                        error
                );
                requireFinalized(message, updated, InventoryOutboxStatus.PENDING);
            });
        } catch (RuntimeException finalizeException) {
            log.error(
                    "Cannot schedule retry for inventory outbox message id={}; "
                            + "the lease will allow redelivery",
                    message.getId(),
                    finalizeException
            );
            return;
        }

        if (message.getAttemptCount() >= properties.getAlertAfterAttempts()) {
            log.error(
                    "Inventory outbox message id={} still failing after attempt={}; "
                            + "nextAttemptAt={}, error={}",
                    message.getId(),
                    message.getAttemptCount(),
                    nextAttemptAt,
                    error
            );
        } else {
            log.warn(
                    "Inventory outbox message id={} publish failed at attempt={}; "
                            + "nextAttemptAt={}, error={}",
                    message.getId(),
                    message.getAttemptCount(),
                    nextAttemptAt,
                    error
            );
        }
    }

    private void markFailed(
            InventoryOutboxMessage message,
            Exception exception
    ) {
        String error = errorSummary(exception);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                int updated = outboxRepository.markFailed(
                        message.getId(),
                        message.getLockToken(),
                        InventoryOutboxStatus.PROCESSING,
                        InventoryOutboxStatus.FAILED,
                        error
                );
                requireFinalized(message, updated, InventoryOutboxStatus.FAILED);
            });
            log.error(
                    "Inventory outbox message id={} permanently failed at attempt={}; error={}",
                    message.getId(),
                    message.getAttemptCount(),
                    error
            );
        } catch (RuntimeException finalizeException) {
            log.error(
                    "Cannot mark inventory outbox message id={} as FAILED; "
                            + "the lease will allow redelivery",
                    message.getId(),
                    finalizeException
            );
        }
    }

    private Duration backoffFor(InventoryOutboxMessage message) {
        double multiplier = Math.pow(
                properties.getRetryMultiplier(),
                Math.max(0, message.getAttemptCount() - 1)
        );
        long delayMillis = (long) Math.min(
                properties.getRetryInitialDelay().toMillis() * multiplier,
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
            InventoryOutboxMessage message,
            int updated,
            InventoryOutboxStatus targetStatus
    ) {
        if (updated != 1) {
            throw new IllegalStateException(
                    "Inventory outbox message %d was not finalized as %s"
                            .formatted(message.getId(), targetStatus)
            );
        }
    }
}
