package com.app.paymentgateway.service;

import com.app.paymentgateway.config.PaymentOutboxProperties;
import com.app.paymentgateway.entity.PaymentOutboxMessage;
import com.app.paymentgateway.repository.PaymentOutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Đẩy message trong bảng outbox lên Kafka theo lô.
 *
 * <p>Mỗi vòng đi qua 4 bước: claim một lô -> bắn hết cả lô -> thu kết quả từng
 * message -> chốt trạng thái. Điểm cốt lõi là bước bắn và bước thu tách rời
 * nhau: {@code send()} chỉ ghi record vào buffer của producer rồi trả future
 * ngay, nên bắn xong cả lô mới đi thu thì producer có đủ record trong buffer để
 * tự gom thành batch. Chờ ngay sau mỗi lần bắn thì mỗi message tốn trọn một
 * vòng round-trip.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOutboxRelay {

    private final PaymentOutboxMessageRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PaymentOutboxProperties properties;
    private final TransactionTemplate transactionTemplate;

    /** Một message và lý do nó rớt; {@code error} null nghĩa là gửi được. */
    private record Outcome(PaymentOutboxMessage message, Throwable error) {
    }

    @Scheduled(
            initialDelayString = "${app.outbox.initial-delay}",
            fixedDelayString = "${app.outbox.fixed-delay}"
    )
    public void publishPendingMessages() {
        String lockToken = UUID.randomUUID().toString();

        List<PaymentOutboxMessage> claimed = claimMessages(lockToken);
        if (claimed.isEmpty()) {
            return;
        }

        finalizeAll(sendAll(claimed), lockToken);
    }

    private List<PaymentOutboxMessage> claimMessages(String lockToken) {
        List<PaymentOutboxMessage> claimed = transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            List<PaymentOutboxMessage> claimable = outboxRepository.findClaimableForUpdate(
                    now,
                    properties.getBatchSize()
            );
            claimable.forEach(message -> message.claim(
                    lockToken,
                    now.plus(properties.getLeaseDuration())
            ));
            return List.copyOf(claimable);
        });
        return claimed == null ? List.of() : claimed;
    }

    /**
     * Bắn cả lô rồi mới thu kết quả.
     *
     * <p>{@code toList()} ở giữa là bắt buộc, không phải cho đẹp: stream chạy
     * lazy nên nếu nối thẳng {@code map(join)} vào cùng một chuỗi thì mỗi phần
     * tử sẽ đi hết đường ống trước khi tới phần tử sau -- bắn 1, chờ 1, bắn 2,
     * chờ 2... tức là quay về gửi tuần tự.
     *
     * <p>{@code orTimeout} của cả lô được gọi ở lượt đầu, cách nhau vài micro
     * giây, nên coi như một hạn chót chung: Kafka chết thì cả lô rớt sau một
     * {@code sendTimeout}, không phải batchSize lần sendTimeout.
     */
    private List<Outcome> sendAll(List<PaymentOutboxMessage> messages) {
        return messages.stream()
                .map(message -> send(message)
                        .orTimeout(
                                properties.getSendTimeout().toMillis(),
                                TimeUnit.MILLISECONDS
                        )
                        // handle nuốt lỗi tại chỗ -> future sau đây không bao
                        // giờ hỏng, nên join phía dưới khỏi cần try/catch.
                        .handle((sent, error) -> new Outcome(message, error)))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .toList();
    }

    /** send() ném thẳng tại chỗ khi hết max.block.ms mà chưa có metadata. */
    private CompletableFuture<SendResult<String, String>> send(PaymentOutboxMessage message) {
        try {
            return kafkaTemplate.send(
                    message.getTopic(),
                    message.getKey(),
                    message.getPayload()
            );
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /**
     * Chốt cả lô trong một transaction. Một message rớt chỉ mình nó bị thử lại,
     * không kéo theo những message đậu cùng lô.
     */
    private void finalizeAll(List<Outcome> outcomes, String lockToken) {
        List<Long> published = idsOf(outcomes, outcome -> outcome.error() == null);
        // attemptCount đã tăng lúc claim, nên so thẳng với maxAttempts.
        List<Long> retryable = idsOf(outcomes, outcome -> outcome.error() != null
                && outcome.message().getAttemptCount() < properties.getMaxAttempts());
        List<Long> exhausted = idsOf(outcomes, outcome -> outcome.error() != null
                && outcome.message().getAttemptCount() >= properties.getMaxAttempts());

        String lastError = outcomes.stream()
                .map(Outcome::error)
                .filter(Objects::nonNull)
                .findFirst()
                .map(this::summarize)
                .orElse(null);
        Instant now = Instant.now();

        transactionTemplate.executeWithoutResult(status -> {
            if (!published.isEmpty()) {
                outboxRepository.markPublishedAll(published, lockToken, now);
            }
            if (!retryable.isEmpty()) {
                outboxRepository.scheduleRetryAll(
                        retryable,
                        lockToken,
                        now.plus(properties.getRetryDelay()),
                        lastError
                );
            }
            if (!exhausted.isEmpty()) {
                outboxRepository.markFailedAll(exhausted, lockToken, lastError);
            }
        });

        if (!retryable.isEmpty() || !exhausted.isEmpty()) {
            log.warn(
                    "Payment outbox: published={}, retry={}, failed={}, error={}",
                    published.size(),
                    retryable.size(),
                    exhausted.size(),
                    lastError
            );
        }
    }

    private List<Long> idsOf(
            List<Outcome> outcomes,
            Predicate<Outcome> filter
    ) {
        return outcomes.stream()
                .filter(filter)
                .map(outcome -> outcome.message().getId())
                .toList();
    }

    private String summarize(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String summary = root.getClass().getSimpleName() + ": " + root.getMessage();
        return summary.length() > 2000 ? summary.substring(0, 2000) : summary;
    }
}
