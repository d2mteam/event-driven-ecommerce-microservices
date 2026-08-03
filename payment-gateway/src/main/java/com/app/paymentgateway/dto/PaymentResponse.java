package com.app.paymentgateway.dto;

import com.app.paymentgateway.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        Long id,
        UUID orderId,
        BigDecimal amount,
        PaymentStatus status,
        Instant expiresAt,
        String paymentUrl
) {
}
