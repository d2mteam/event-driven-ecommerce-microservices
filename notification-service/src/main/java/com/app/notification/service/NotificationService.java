package com.app.notification.service;

import com.app.notification.dto.NotificationResponse;
import com.app.notification.dto.PageResponse;
import com.app.notification.entity.Notification;
import com.app.notification.mapper.NotificationMapper;
import com.app.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
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
    public Notification save(Notification notification) {
        return notificationRepository.findByOrderId(notification.getOrderId())
                .map(existing -> {
                    existing.replaceMessage(notification.getMessage());
                    return existing;
                })
                .orElseGet(() -> notificationRepository.save(notification));
    }
}
