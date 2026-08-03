package com.app.order.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.clients")
public class ClientProperties {

    @NotBlank
    private String productBaseUrl;

    @NotBlank
    private String inventoryBaseUrl;

    @NotBlank
    private String paymentBaseUrl;

    @NotNull
    private Duration connectTimeout;

    @NotNull
    private Duration readTimeout;
}
