package com.app.notification.service;

import com.app.notification.entity.Notification;
import com.app.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class NotificationStream {

    private final ConcurrentHashMap<UUID, Set<SseEmitter>> emittersByUser =
            new ConcurrentHashMap<>();
    private final NotificationMapper notificationMapper;

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emittersByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet())
                .add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(ignored -> remove(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data(""));
        } catch (IOException exception) {
            remove(userId, emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void publish(Notification notification) {
        Set<SseEmitter> emitters = emittersByUser.get(notification.getUserId());
        if (emitters == null) {
            return;
        }

        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .id(notification.getId().toString())
                        .name("notification")
                        .data(notificationMapper.toResponse(notification)));
            } catch (IOException exception) {
                remove(notification.getUserId(), emitter);
                emitter.complete();
            }
        });
    }

    private void remove(UUID userId, SseEmitter emitter) {
        emittersByUser.computeIfPresent(userId, (ignored, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
