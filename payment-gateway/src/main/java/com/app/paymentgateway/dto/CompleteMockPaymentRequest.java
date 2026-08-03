package com.app.paymentgateway.dto;

import com.app.paymentgateway.model.MockPaymentResult;
import jakarta.validation.constraints.NotNull;

public record CompleteMockPaymentRequest(
        @NotNull MockPaymentResult result
) {
}
