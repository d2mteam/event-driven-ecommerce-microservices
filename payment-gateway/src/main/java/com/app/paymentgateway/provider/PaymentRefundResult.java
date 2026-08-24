package com.app.paymentgateway.provider;

public record PaymentRefundResult(
        boolean successful,
        boolean retryable,
        String message
) {

    public static PaymentRefundResult success() {
        return new PaymentRefundResult(true, false, "Refund succeeded");
    }

    public static PaymentRefundResult retryable(String message) {
        return new PaymentRefundResult(false, true, message);
    }

    public static PaymentRefundResult rejected(String message) {
        return new PaymentRefundResult(false, false, message);
    }
}
