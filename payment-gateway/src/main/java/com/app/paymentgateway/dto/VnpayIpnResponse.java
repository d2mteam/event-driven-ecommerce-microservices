package com.app.paymentgateway.dto;

public record VnpayIpnResponse(
        String RspCode,
        String Message
) {
}
