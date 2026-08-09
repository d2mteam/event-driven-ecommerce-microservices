package com.app.paymentgateway.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.kafka.retry")
public record KafkaRetryProperties(
        @Min(1) int maxAttempts,
        @NotNull @DurationMin(millis = 1) Duration initialInterval,
        @DecimalMin("1.0") double multiplier,
        @NotNull @DurationMin(millis = 1) Duration maxInterval,
        @NotBlank String deadLetterTopic,
        @Min(1) int partitions,
        @Min(1) @Max(Short.MAX_VALUE) int replicationFactor
) {
}
