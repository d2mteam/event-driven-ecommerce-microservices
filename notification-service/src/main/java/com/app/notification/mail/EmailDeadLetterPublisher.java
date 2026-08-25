package com.app.notification.mail;

import com.app.notification.config.KafkaRetryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Email nào gửi hỏng thì đẩy nguyên payload sang DLT, không ném lên listener --
 * một email hỏng không kéo cả lô phải gửi lại.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDeadLetterPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaRetryProperties retryProperties;

    public void publish(String payload, Exception cause) {
        log.warn("Email failed, sending to DLT", cause);
        kafkaTemplate.send(retryProperties.emailDeadLetterTopic(), payload);
    }
}
