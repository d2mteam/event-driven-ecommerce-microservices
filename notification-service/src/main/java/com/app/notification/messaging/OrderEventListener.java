package com.app.notification.messaging;

import com.app.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderEventParser eventParser;
    private final NotificationService notificationService;

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
        notificationService.save(notification);
    }
}
