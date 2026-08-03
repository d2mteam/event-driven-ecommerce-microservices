package com.app.paymentgateway.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private final PaymentMessagingProperties properties;

    public KafkaTopicConfig(PaymentMessagingProperties properties) {
        this.properties = properties;
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder
                .name(properties.getTopics().getPaymentEvents())
                .partitions(properties.getTopic().getPartitions())
                .replicas(properties.getTopic().getReplicationFactor())
                .config(
                        TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG,
                        String.valueOf(
                                properties.getTopic().getMinInSyncReplicas()
                        )
                )
                .build();
    }
}
