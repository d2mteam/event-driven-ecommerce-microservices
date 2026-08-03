package com.app.notification.config;

import com.app.notification.messaging.NonRetryableOrderEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaRetryProperties properties
    ) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) -> new TopicPartition(
                                properties.deadLetterTopic(),
                                record.partition()
                        )
                );
        recoverer.setFailIfSendResultIsError(true);

        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(
                        properties.maxAttempts() - 1
                );
        backOff.setInitialInterval(properties.initialInterval().toMillis());
        backOff.setMultiplier(properties.multiplier());
        backOff.setMaxInterval(properties.maxInterval().toMillis());

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(
                NonRetryableOrderEventException.class
        );
        return errorHandler;
    }

    @Bean
    public NewTopic notificationDeadLetterTopic(
            KafkaRetryProperties properties
    ) {
        return TopicBuilder.name(properties.deadLetterTopic())
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }
}
