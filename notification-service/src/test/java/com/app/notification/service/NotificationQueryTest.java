package com.app.notification.service;

import com.app.notification.config.NotificationProperties;
import com.app.notification.dto.NotificationResponse;
import com.app.notification.entity.Notification;
import com.app.notification.mapper.NotificationMapper;
import com.app.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationQueryTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private NotificationProperties notificationProperties;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void returnsOnePageForTheAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(11L)
                .userId(userId)
                .orderId(orderId)
                .message("Đơn hàng đã được xác nhận.")
                .sent(true)
                .createdAt(Instant.parse("2026-08-09T08:00:00Z"))
                .build();
        NotificationResponse response = new NotificationResponse(
                11L,
                orderId,
                notification.getMessage(),
                notification.getCreatedAt()
        );
        when(notificationRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(notification)));
        when(notificationMapper.toResponse(notification)).thenReturn(response);

        var result = notificationService.findAll(userId, 2, 10);

        assertThat(result.content()).containsExactly(response);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository)
                .findAllByUserIdOrderByCreatedAtDescIdDesc(
                        eq(userId),
                        pageable.capture()
                );
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
    }
}
