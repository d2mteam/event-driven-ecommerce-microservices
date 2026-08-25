package com.app.notification.config;

import com.app.notification.exception.NonRetryableEmailEventException;
import com.app.notification.exception.NonRetryableOrderEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.HashMap;
import java.util.Map;

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

    /**
     * Luồng email chạy theo lô: mỗi lần poll tối đa batchSize record, broker
     * chờ gom tối đa window.
     *
     * <p>fetch.min.bytes vẫn để mặc định (1 byte) nên hễ có record là broker
     * trả luôn -- window là trần chờ chứ không phải lúc nào cũng chờ đủ.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
    notificationEmailKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaRetryProperties properties,
            EmailBatchProperties batchProperties
    ) {
        Map<String, Object> batchConfig = new HashMap<>(
                consumerFactory.getConfigurationProperties()
        );
        batchConfig.put(
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                batchProperties.batchSize()
        );
        batchConfig.put(
                ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG,
                (int) batchProperties.window().toMillis()
        );
        var batchConsumerFactory =
                new DefaultKafkaConsumerFactory<String, String>(batchConfig);

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(batchConsumerFactory);
        factory.setBatchListener(true);
        factory.setCommonErrorHandler(errorHandler(
                kafkaTemplate,
                properties,
                properties.emailDeadLetterTopic()
        ));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        return factory;
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
