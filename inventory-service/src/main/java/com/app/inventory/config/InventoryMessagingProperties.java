package com.app.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.messaging.topics")
public record InventoryMessagingProperties(String inventoryEvents) {
}
