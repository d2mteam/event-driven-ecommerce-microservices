package com.app.inventory.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.inventory.outbox")
public class InventoryOutboxProperties {

    @NotNull
    private Duration initialDelay = Duration.ofSeconds(2);

    @NotNull
    private Duration fixedDelay = Duration.ofSeconds(1);

    @Min(1)
    private int batchSize = 50;

    @NotNull
    private Duration sendTimeout = Duration.ofSeconds(10);

    @NotNull
    private Duration leaseDuration = Duration.ofMinutes(10);

    @NotNull
    private Duration retryInitialDelay = Duration.ofSeconds(1);

    @DecimalMin("1.0")
    private double retryMultiplier = 2.0;

    @NotNull
    private Duration retryMaxDelay = Duration.ofSeconds(60);

    @Min(1)
    private int alertAfterAttempts = 10;

    @AssertTrue(message = "lease-duration must exceed batch-size multiplied by send-timeout")
    public boolean isLeaseLongEnough() {
        if (leaseDuration == null || sendTimeout == null || batchSize < 1) {
            return true;
        }
        return leaseDuration.compareTo(sendTimeout.multipliedBy(batchSize)) > 0;
    }
}
