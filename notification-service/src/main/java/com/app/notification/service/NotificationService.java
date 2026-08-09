package com.app.notification.service;

import com.app.notification.config.NotificationProperties;
import com.app.notification.dto.NotificationResponse;
import com.app.notification.dto.PageResponse;
import com.app.notification.entity.Notification;
import com.app.notification.event.OrderConfirmedEvent;
import com.app.notification.event.OrderFailedEvent;
import com.app.notification.event.OrderCancelledEvent;
import com.app.notification.mapper.NotificationMapper;
import com.app.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationProperties notificationProperties;
    private final NotificationMapper notificationMapper;

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

    @Transactional
    public void createSuccessFor(OrderConfirmedEvent event) {
        if (notificationRepository.existsByOrderId(event.orderId())) {
            return;
        }

        notificationRepository.save(
                notificationMapper.toNotification(event, notificationProperties)
        );
    }

    @Transactional
    public void replaceWithFailure(OrderFailedEvent event) {
        Notification notification = notificationRepository
                .findByOrderId(event.orderId())
                .orElseGet(() -> Notification.builder()
                        .userId(event.userId())
                        .orderId(event.orderId())
                        .build());

        notification.replaceMessage(
                notificationMapper.toFailureMessage(
                        event,
                        notificationProperties
                )
        );
        notificationRepository.save(notification);
    }

    @Transactional
    public void replaceWithCancellation(OrderCancelledEvent event) {
        Notification notification = notificationRepository
                .findByOrderId(event.orderId())
                .orElseGet(() -> Notification.builder()
                        .userId(event.userId())
                        .orderId(event.orderId())
                        .build());

        notification.replaceMessage(
                notificationMapper.toCancellationMessage(
                        event,
                        notificationProperties
                )
        );
        notificationRepository.save(notification);
    }
}
