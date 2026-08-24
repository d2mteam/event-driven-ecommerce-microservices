package com.app.paymentgateway.provider;

public record VnpayNotification(
        Long paymentId,
        long amount,
        String transactionNo,
        String responseCode,
        String transactionStatus
) {

    public boolean successful() {
        return "00".equals(responseCode) && "00".equals(transactionStatus);
    }
}
