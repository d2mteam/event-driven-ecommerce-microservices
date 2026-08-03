package com.app.paymentgateway.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        @NotNull Duration ttl,
        @NotNull Duration sweepDelay,
        @NotBlank String publicBaseUrl
) {
}
