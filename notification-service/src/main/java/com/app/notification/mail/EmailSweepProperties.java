package com.app.notification.mail;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.notification.email")
public record EmailSweepProperties(
        @NotNull Duration sweepDelay,
        @NotNull Duration leaseDuration,
        @Min(1) int sweepBatchSize,
        @Min(1) int maxSweepBatchesPerRun,
        @Min(1) int maxAttempts
) {
}
