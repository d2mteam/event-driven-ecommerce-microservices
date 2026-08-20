package com.app.notification.mail;

import com.app.notification.entity.Notification;
import com.app.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Luồng gửi mail, tách hẳn khỏi luồng tiêu thụ Kafka (OrderEventListener).
 * Chạy theo lịch riêng, claim theo lease -- SMTP chậm hay lỗi không ảnh
 * hưởng gì tới việc consume Kafka, và nhiều instance chạy song song không
 * giành trúng cùng dòng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEmailSweeper {

    private final NotificationService notificationService;
    private final EmailSweepProperties properties;

    @Scheduled(
            initialDelayString = "${app.notification.email.sweep-delay}",
            fixedDelayString = "${app.notification.email.sweep-delay}"
    )
    public void sendPendingEmails() {
        int totalClaimed = 0;

        try {
            for (int batch = 0; batch < properties.maxSweepBatchesPerRun(); batch++) {
                List<Notification> claimed = notificationService.claimEmailBatch(
                        properties.sweepBatchSize(),
                        properties.leaseDuration()
                );
                if (claimed.isEmpty()) {
                    break;
                }

                notificationService.sendClaimedBatch(claimed, properties.maxAttempts());
                totalClaimed += claimed.size();

                if (claimed.size() < properties.sweepBatchSize()) {
                    break;
                }
            }
        } catch (RuntimeException exception) {
            log.error("Notification email sweep failed", exception);
            return;
        }

        if (totalClaimed > 0) {
            log.info("Processed {} pending notification emails", totalClaimed);
        }
    }
}
