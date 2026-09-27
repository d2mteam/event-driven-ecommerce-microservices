package com.app.paymentgateway.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * deadline: order-service suy ra từ hạn giữ hàng; payment không được sống quá
 * mốc này. null nghĩa là chỉ dùng TTL của payment.
 */
public record CreatePaymentRequest(
        @NotNull UUID orderId,
        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount,
        @NotBlank @Size(max = 45) String clientIp,
        @Future Instant deadline
) {
}
