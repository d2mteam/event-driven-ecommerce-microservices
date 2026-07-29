package com.app.inventory.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.inventory.reservation")
public record InventoryReservationProperties(
        Duration ttl,
        Duration sweepDelay,
        Duration eventRelayDelay,
        int eventRelayBatchSize,
        Duration eventSendTimeout
) {
}
