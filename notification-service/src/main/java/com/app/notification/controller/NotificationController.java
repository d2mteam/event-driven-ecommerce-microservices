package com.app.notification.controller;

import com.app.notification.dto.NotificationResponse;
import com.app.notification.dto.PageResponse;
import com.app.notification.service.NotificationService;
import com.app.notification.service.NotificationStream;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final NotificationService notificationService;
    private final NotificationStream notificationStream;

    @GetMapping
    public PageResponse<NotificationResponse> findAll(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
    ) {
        return notificationService.findAll(userId, page, size);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(USER_ID_HEADER) UUID userId) {
        return notificationStream.subscribe(userId);
    }
}
