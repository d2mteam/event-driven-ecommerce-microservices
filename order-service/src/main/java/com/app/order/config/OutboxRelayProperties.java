package com.app.order.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxRelayProperties {

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
