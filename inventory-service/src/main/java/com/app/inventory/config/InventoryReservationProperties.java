package com.app.inventory.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "app.inventory.reservation")
public record InventoryReservationProperties(
        Duration ttl,
        Duration sweepDelay,
        @Min(1)
        int batchSize,
        @Min(1)
        int maxBatchesPerRun
) {
}
