package com.app.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private final KafkaMessagingProperties properties;

    public KafkaTopicConfig(KafkaMessagingProperties properties) {
        this.properties = properties;
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return buildTopic(properties.getTopics().getOrderEvents());
    }

    @Bean
    public NewTopic inventoryEventsTopic() {
        return buildTopic(properties.getTopics().getInventoryEvents());
    }

    private NewTopic buildTopic(String name) {
        return TopicBuilder.name(name)
                .partitions(properties.getTopic().getPartitions())
                .replicas(properties.getTopic().getReplicationFactor())
                .config(
                        TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG,
                        String.valueOf(properties.getTopic().getMinInSyncReplicas())
                )
                .build();
    }
}
