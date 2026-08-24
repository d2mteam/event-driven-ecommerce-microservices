package com.app.paymentgateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.payment.vnpay")
public record VnpayProperties(
        @NotBlank String payUrl,
        @NotBlank String apiUrl,
        @NotBlank String tmnCode,
        @NotBlank String hashSecret,
        @NotBlank String returnUrl,
        @NotBlank String frontendBaseUrl,
        @NotBlank String version,
        @NotBlank String orderType,
        @NotBlank String locale
) {
}
