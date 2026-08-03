package com.app.paymentgateway.config;

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
public class PaymentOutboxProperties {

    @Min(1)
    private int batchSize;

    @NotNull
    private Duration sendTimeout;
}
