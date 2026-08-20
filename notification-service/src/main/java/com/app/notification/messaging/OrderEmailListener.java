package com.app.notification.messaging;

import com.app.notification.exception.NonRetryableEmailEventException;
import com.app.notification.mail.EmailNotifier;
import com.app.notification.mail.UserEmailClient;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEmailListener {

    private final OrderEventParser eventParser;
    private final UserEmailClient userEmailClient;
    private final EmailNotifier emailNotifier;

    @KafkaListener(
            topics = "${app.messaging.topics.order-events}",
            groupId = "${app.messaging.email.group-id}",
            containerFactory = "notificationEmailKafkaListenerContainerFactory"
    )
    public void consume(String payload) {
        var notification = eventParser.parse(payload).orElse(null);
        if (notification == null) {
            return;
        }
        String email = userEmailClient.findEmail(notification.getUserId());
        if (email == null) {
            throw new NonRetryableEmailEventException(
                    "Email not found for user " + notification.getUserId()
            );
        }
        emailNotifier.send(email, notification.getMessage());
    }
}
