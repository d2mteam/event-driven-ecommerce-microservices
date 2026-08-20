package com.app.notification.messaging;

import com.app.notification.service.NotificationService;
import com.app.notification.service.NotificationStream;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderEventParser eventParser;
    private final NotificationService notificationService;
    private final NotificationStream notificationStream;

    @KafkaListener(
            topics = "${app.messaging.topics.order-events}",
            groupId = "${app.messaging.history.group-id}",
            containerFactory = "notificationHistoryKafkaListenerContainerFactory"
    )
    public void consume(String payload) {
        var notification = eventParser.parse(payload).orElse(null);
        if (notification == null) {
            return;
        }
        var saved = notificationService.save(notification);
        notificationStream.publish(saved);
    }
}
