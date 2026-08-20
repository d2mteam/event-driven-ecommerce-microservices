package com.app.notification.config;

import com.app.notification.exception.NonRetryableEmailEventException;
import com.app.notification.exception.NonRetryableOrderEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
    notificationHistoryKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaRetryProperties properties
    ) {
        return listenerFactory(
                consumerFactory,
                errorHandler(
                        kafkaTemplate,
                        properties,
                        properties.historyDeadLetterTopic()
                )
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
    notificationEmailKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaRetryProperties properties
    ) {
        return listenerFactory(
                consumerFactory,
                errorHandler(
                        kafkaTemplate,
                        properties,
                        properties.emailDeadLetterTopic()
                )
        );
    }

    @Bean
    public NewTopic historyDeadLetterTopic(KafkaRetryProperties properties) {
        return topic(properties.historyDeadLetterTopic(), properties);
    }

    @Bean
    public NewTopic emailDeadLetterTopic(KafkaRetryProperties properties) {
        return topic(properties.emailDeadLetterTopic(), properties);
    }

    private ConcurrentKafkaListenerContainerFactory<String, String> listenerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler errorHandler
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    private DefaultErrorHandler errorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaRetryProperties properties,
            String deadLetterTopic
    ) {
        var recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        deadLetterTopic,
                        record.partition()
                )
        );
        recoverer.setFailIfSendResultIsError(true);

        var backOff = new ExponentialBackOffWithMaxRetries(
                properties.maxAttempts() - 1
        );
        backOff.setInitialInterval(properties.initialInterval().toMillis());
        backOff.setMultiplier(properties.multiplier());
        backOff.setMaxInterval(properties.maxInterval().toMillis());

        var errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(
                NonRetryableOrderEventException.class,
                NonRetryableEmailEventException.class
        );
        return errorHandler;
    }

    private NewTopic topic(String name, KafkaRetryProperties properties) {
        return TopicBuilder.name(name)
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }
}
