package com.app.notification.messaging;

import com.app.notification.config.EmailBatchProperties;
import com.app.notification.entity.Notification;
import com.app.notification.exception.NonRetryableOrderEventException;
import com.app.notification.mail.EmailDeadLetterPublisher;
import com.app.notification.mail.EmailNotifier;
import com.app.notification.mail.UserEmailClient;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Nhận cả lô (tối đa batchSize record, chờ gom tối đa window) rồi gửi ĐỒNG THỜI
 * -- không email nào chặn email nào.
 *
 * <p>Đồng thời chứ không phải song song: gọi SMTP xong là thread nằm chờ mạng,
 * không dùng CPU. Thứ cần là nhiều phiên SMTP cùng bay, không phải nhiều core.
 * Vì vậy dùng virtual thread thay cho ForkJoinPool.commonPool: pool đó nhắm vào
 * việc CPU-bound nên đặt parallelism theo số core, nhét I/O chặn vào là mỗi
 * task chiếm một slot để ngồi không -- và khi container bị giới hạn CPU thì
 * parallelism tụt về 1, mail quay lại gửi tuần tự.
 *
 * <p>Xử lý lỗi: payload hỏng thì bỏ qua vì gửi lại cũng hỏng y hệt; mọi lỗi còn
 * lại đẩy payload sang DLT để xử lý sau. Listener không ném lên trên, nên một
 * email hỏng không làm cả lô bị gửi lại.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEmailListener {

    private final OrderEventParser eventParser;
    private final UserEmailClient userEmailClient;
    private final EmailNotifier emailNotifier;
    private final EmailDeadLetterPublisher deadLetterPublisher;
    private final EmailBatchProperties batchProperties;

    /** Chặn trên virtual thread chỉ park thread ảo và trả carrier thread lại,
     *  nên số mail bay cùng lúc không phụ thuộc số core. */
    private final ExecutorService emailExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    void shutdownExecutor() {
        emailExecutor.shutdown();
    }

    @KafkaListener(
            topics = "${app.messaging.topics.order-events}",
            groupId = "${app.messaging.email.group-id}",
            containerFactory = "notificationEmailKafkaListenerContainerFactory"
    )
    public void consume(List<String> payloads) {
        List<PendingEmail> pending = parseAll(payloads);
        if (pending.isEmpty()) {
            return;
        }

        // User Service chết thì ném lên listener: cả lô đáng được retry, khác
        // hẳn lỗi của riêng một email.
        Map<UUID, String> emailsByUser = userEmailClient.findEmails(
                pending.stream()
                        .map(item -> item.notification().getUserId())
                        .collect(Collectors.toSet())
        );

        CompletableFuture<?>[] futures = pending.stream()
                .map(item -> CompletableFuture.runAsync(
                        () -> send(item, emailsByUser.get(item.notification().getUserId())),
                        emailExecutor
                ))
                .toArray(CompletableFuture[]::new);

        awaitWithinWindow(futures);
    }

    private List<PendingEmail> parseAll(List<String> payloads) {
        List<PendingEmail> pending = new ArrayList<>();
        for (String payload : payloads) {
            try {
                eventParser.parse(payload).ifPresent(
                        notification -> pending.add(new PendingEmail(payload, notification))
                );
            } catch (NonRetryableOrderEventException exception) {
                log.warn("Skip malformed order event", exception);
            }
        }
        return pending;
    }

    /** Chạy trên virtual thread riêng nên phải tự nuốt lỗi. */
    private void send(PendingEmail item, String email) {
        try {
            if (email == null) {
                throw new IllegalStateException(
                        "No email for user " + item.notification().getUserId()
                );
            }
            emailNotifier.send(email, item.notification().getMessage());
        } catch (RuntimeException exception) {
            deadLetterPublisher.publish(item.payload(), exception);
        }
    }

    /**
     * Hết cửa sổ mà chưa xong thì thôi không chờ nữa để consumer còn poll lô
     * kế -- phần dở dang vẫn chạy nốt ở background, chỉ là không ai chờ.
     */
    private void awaitWithinWindow(CompletableFuture<?>[] futures) {
        try {
            CompletableFuture.allOf(futures)
                    .get(batchProperties.window().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            log.warn("Email batch exceeded {} window", batchProperties.window());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            log.warn("Email batch failed", exception);
        }
    }

    private record PendingEmail(String payload, Notification notification) {
    }
}
