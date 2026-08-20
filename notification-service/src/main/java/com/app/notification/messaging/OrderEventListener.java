package com.app.notification.messaging;

import com.app.notification.config.NotificationProperties;
import com.app.notification.entity.Notification;
import com.app.notification.event.EventVersions;
import com.app.notification.event.OrderConfirmedEvent;
import com.app.notification.event.OrderEventType;
import com.app.notification.event.OrderFailedEvent;
import com.app.notification.event.OrderCancelledEvent;
import com.app.notification.mapper.NotificationMapper;
import com.app.notification.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Nhận cả lô message một lần thay vì từng cái — max-poll-records giới hạn
 * lô tối đa 50. Cả lô cùng đậu hoặc cùng lỗi: một payload hỏng ném exception
 * thì error handler coi cả lô là thất bại, retry rồi DLT cả lô (kể cả những
 * record hợp lệ đứng cạnh). Chấp nhận đánh đổi này để giữ code đơn giản.
 *
 * <p>Chỉ làm một việc: parse rồi lưu DB (status PENDING). KHÔNG gửi mail ở
 * đây — gửi mail là {@link com.app.notification.mail.NotificationEmailSweeper},
 * chạy theo lịch riêng, tách hẳn khỏi tốc độ tiêu thụ Kafka. Lý do tách: mail
 * chậm/lỗi không được làm nghẽn hay lặp lại việc consume Kafka.
 */
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final ObjectMapper objectMapper;
    private final NotificationMapper notificationMapper;
    private final NotificationProperties notificationProperties;
    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${app.messaging.topics.order-events}",
            groupId = "${app.messaging.notification.group-id}"
    )
    public void consume(List<String> payloads) {
        List<Notification> notifications = new ArrayList<>();

        for (String payload : payloads) {
            JsonNode eventJson = readEvent(payload);
            String eventType = requiredText(eventJson, "eventType");

            if (OrderEventType.ORDER_CONFIRMED.name().equals(eventType)) {
                notifications.add(toNotificationFromConfirmed(eventJson));
            } else if (OrderEventType.ORDER_FAILED.name().equals(eventType)) {
                notifications.add(toNotificationFromFailed(eventJson));
            } else if (OrderEventType.ORDER_CANCELLED.name().equals(eventType)) {
                notifications.add(toNotificationFromCancelled(eventJson));
            } else if (!OrderEventType.ORDER_CANCELLATION_REQUESTED.name()
                    .equals(eventType)) {
                throw invalidEvent("Unknown order event type: " + eventType);
            }
        }

        notificationService.upsertAll(notifications);
    }

    private Notification toNotificationFromConfirmed(JsonNode eventJson) {
        OrderConfirmedEvent event = readEvent(eventJson, OrderConfirmedEvent.class);
        if (event.eventVersion() != EventVersions.ORDER_CONFIRMED) {
            throw invalidEvent(
                    "Unsupported OrderConfirmedEvent version: " + event.eventVersion());
        }
        return notificationMapper.toNotification(event, notificationProperties);
    }

    private Notification toNotificationFromFailed(JsonNode eventJson) {
        OrderFailedEvent event = readEvent(eventJson, OrderFailedEvent.class);
        if (event.eventVersion() != EventVersions.ORDER_FAILED) {
            throw invalidEvent(
                    "Unsupported OrderFailedEvent version: " + event.eventVersion());
        }
        return Notification.builder()
                .userId(event.userId())
                .orderId(event.orderId())
                .message(notificationMapper.toFailureMessage(event, notificationProperties))
                .build();
    }

    private Notification toNotificationFromCancelled(JsonNode eventJson) {
        OrderCancelledEvent event = readEvent(eventJson, OrderCancelledEvent.class);
        if (event.eventVersion() != EventVersions.ORDER_CANCELLED) {
            throw invalidEvent(
                    "Unsupported OrderCancelledEvent version: " + event.eventVersion());
        }
        return Notification.builder()
                .userId(event.userId())
                .orderId(event.orderId())
                .message(notificationMapper.toCancellationMessage(event, notificationProperties))
                .build();
    }

    private JsonNode readEvent(String payload) {
        if (payload == null) {
            throw invalidEvent("Order event payload is null");
        }
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new NonRetryableOrderEventException(
                    "Malformed order event JSON",
                    exception
            );
        }
    }

    private <T> T readEvent(JsonNode eventJson, Class<T> eventType) {
        try {
            return objectMapper.treeToValue(eventJson, eventType);
        } catch (JsonProcessingException exception) {
            throw new NonRetryableOrderEventException(
                    "Invalid " + eventType.getSimpleName(),
                    exception
            );
        }
    }

    private String requiredText(JsonNode eventJson, String field) {
        if (eventJson == null || !eventJson.isObject()
                || !eventJson.hasNonNull(field)) {
            throw invalidEvent("Missing required field: " + field);
        }
        String value = eventJson.get(field).asText();
        if (value.isBlank()) {
            throw invalidEvent("Required field is blank: " + field);
        }
        return value;
    }

    private NonRetryableOrderEventException invalidEvent(String message) {
        return new NonRetryableOrderEventException(message);
    }
}
