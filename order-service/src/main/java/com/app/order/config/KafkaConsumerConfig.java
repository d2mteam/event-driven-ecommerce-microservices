package com.app.order.config;

import com.app.order.messaging.NonRetryableOrderEventException;
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
            KafkaMessagingProperties messagingProperties,
            KafkaRetryProperties retryProperties
    ) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) -> new TopicPartition(
                                deadLetterTopic(
                                        record.topic(),
                                        messagingProperties,
                                        retryProperties
                                ),
                                record.partition()
                        )
                );
        recoverer.setFailIfSendResultIsError(true);

        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(
                        retryProperties.maxAttempts() - 1
                );
        backOff.setInitialInterval(
                retryProperties.initialInterval().toMillis()
        );
        backOff.setMultiplier(retryProperties.multiplier());
        backOff.setMaxInterval(retryProperties.maxInterval().toMillis());

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(
                NonRetryableOrderEventException.class
        );
        return errorHandler;
    }

    @Bean
    public NewTopic paymentOrderDeadLetterTopic(
            KafkaRetryProperties properties
    ) {
        return deadLetterTopic(
                properties.paymentDeadLetterTopic(),
                properties
        );
    }

    @Bean
    public NewTopic inventoryOrderDeadLetterTopic(
            KafkaRetryProperties properties
    ) {
        return deadLetterTopic(
                properties.inventoryDeadLetterTopic(),
                properties
        );
    }

    private String deadLetterTopic(
            String sourceTopic,
            KafkaMessagingProperties messagingProperties,
            KafkaRetryProperties retryProperties
    ) {
        if (messagingProperties.getTopics().getPaymentEvents()
                .equals(sourceTopic)) {
            return retryProperties.paymentDeadLetterTopic();
        }
        if (messagingProperties.getTopics().getInventoryEvents()
                .equals(sourceTopic)) {
            return retryProperties.inventoryDeadLetterTopic();
        }
        throw new IllegalArgumentException(
                "No dead-letter topic configured for " + sourceTopic
        );
    }

    private NewTopic deadLetterTopic(
            String name,
            KafkaRetryProperties properties
    ) {
        return TopicBuilder.name(name)
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }
}
