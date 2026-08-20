package com.app.notification.service;

import com.app.notification.dto.NotificationResponse;
import com.app.notification.dto.PageResponse;
import com.app.notification.entity.Notification;
import com.app.notification.mail.EmailNotifier;
import com.app.notification.mail.UserEmailCache;
import com.app.notification.mapper.NotificationMapper;
import com.app.notification.model.NotificationStatus;
import com.app.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final EmailNotifier emailNotifier;
    private final UserEmailCache userEmailCache;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> findAll(
            UUID userId,
            int page,
            int size
    ) {
        return PageResponse.from(
                notificationRepository
                        .findAllByUserIdOrderByCreatedAtDescIdDesc(
                                userId,
                                PageRequest.of(page, size)
                        )
                        .map(notificationMapper::toResponse)
        );
    }

    /**
     * Ghi một lô thông báo bằng một saveAll() thay vì mỗi cái một save() riêng.
     *
     * <p>order_id có unique constraint (uk_notifications_order_id) nên không
     * thể insert vô tội vạ: phải tra trước xem order nào trong lô đã có dòng,
     * để update đúng dòng đó thay vì chèn dòng mới trùng khoá.
     *
     * <p>Nếu cùng một orderId xuất hiện hai lần trong lô (hiếm — một đơn chỉ
     * đổi trạng thái một lần), dòng sau ghi đè dòng trước theo thứ tự trong
     * danh sách, không theo thời điểm event thật sự xảy ra. Chưa xử lý, chỉ
     * ghi chú vì hiếm gặp.
     */
    @Transactional
    public void upsertAll(List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return;
        }

        Map<UUID, Notification> existingByOrderId = notificationRepository
                .findAllByOrderIdIn(notifications.stream()
                        .map(Notification::getOrderId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Notification::getOrderId, Function.identity()));

        for (Notification notification : notifications) {
            Notification existing = existingByOrderId.get(notification.getOrderId());
            if (existing != null) {
                existing.replaceMessage(notification.getMessage());
            } else {
                existingByOrderId.put(notification.getOrderId(), notification);
            }
        }

        notificationRepository.saveAll(existingByOrderId.values());
    }

    /**
     * Claim một lô bằng lease (giống order_outbox): SELECT ... FOR UPDATE SKIP
     * LOCKED rồi đánh dấu PROCESSING + lockToken riêng trong CÙNG transaction.
     * Nhiều instance chạy song song sẽ không giành trúng cùng một dòng.
     */
    @Transactional
    public List<Notification> claimEmailBatch(int batchSize, Duration leaseDuration) {
        Instant now = Instant.now();
        String lockToken = UUID.randomUUID().toString();
        Instant lockedUntil = now.plus(leaseDuration);

        List<Notification> claimed = notificationRepository.findClaimableForUpdate(now, batchSize);
        claimed.forEach(notification -> notification.claim(lockToken, lockedUntil));
        notificationRepository.saveAll(claimed);
        return claimed;
    }

    /**
     * Gửi mail cho một lô đã claim. Bắn tất cả song song bằng CompletableFuture
     * -- một mail chậm không chặn các mail còn lại trong lô -- rồi mới ghi kết
     * quả từng dòng xuống DB sau khi tất cả đã xong.
     *
     * <p>Dùng ForkJoinPool.commonPool() mặc định (không executor riêng): đủ
     * cho demo, batch tối đa vài chục mail. Không chỉnh pool size riêng.
     *
     * <p>Ghi kết quả qua markSent/markFailedAttempt (UPDATE có điều kiện
     * id + lockToken), không dùng save() -- để tránh ghi đè một lần claim
     * khác đã lấy lại dòng này vì lease của lần claim cũ đã hết hạn.
     */
    public void sendClaimedBatch(List<Notification> claimed, int maxAttempts) {
        if (claimed.isEmpty()) {
            return;
        }

        Map<UUID, String> emailsByUserId = userEmailCache.resolve(
                claimed.stream().map(Notification::getUserId).collect(Collectors.toSet()));

        List<CompletableFuture<Void>> sends = claimed.stream()
                .map(notification -> CompletableFuture.runAsync(() ->
                        sendAndFinalize(notification, emailsByUserId.get(notification.getUserId()), maxAttempts)))
                .toList();

        CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).join();
    }

    private void sendAndFinalize(Notification notification, String email, int maxAttempts) {
        if (email == null) {
            finalizeFailedAttempt(notification, maxAttempts);
            return;
        }
        try {
            emailNotifier.send(email, notification.getMessage());
            transactionTemplate.executeWithoutResult(ignored -> notificationRepository.markSent(
                    notification.getId(),
                    notification.getLockToken(),
                    NotificationStatus.PROCESSING,
                    NotificationStatus.SENT
            ));
        } catch (RuntimeException exception) {
            finalizeFailedAttempt(notification, maxAttempts);
        }
    }

    /**
     * @Modifying query cần transaction bao quanh -- Spring Data KHÔNG tự mở
     * transaction cho query tuỳ biến (chỉ CRUD method sinh sẵn mới vậy).
     * Gọi qua TransactionTemplate thay vì @Transactional trên method: hàm này
     * chạy trên thread của CompletableFuture, không phải qua Spring proxy của
     * bean này, nên @Transactional bị bỏ qua nếu gọi kiểu this.xxx().
     */
    private void finalizeFailedAttempt(Notification notification, int maxAttempts) {
        int nextAttempts = notification.getAttempts() + 1;
        NotificationStatus nextStatus = nextAttempts >= maxAttempts
                ? NotificationStatus.FAILED
                : NotificationStatus.PENDING;
        transactionTemplate.executeWithoutResult(ignored -> notificationRepository.markFailedAttempt(
                notification.getId(),
                notification.getLockToken(),
                NotificationStatus.PROCESSING,
                nextStatus,
                nextAttempts
        ));
    }
}
