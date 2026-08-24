package com.app.paymentgateway.exception;

import lombok.Getter;

@Getter
public class VnpayCallbackException extends RuntimeException {

    private final String responseCode;

    public VnpayCallbackException(String responseCode, String message) {
        super(message);
        this.responseCode = responseCode;
    }

    public VnpayCallbackException(
            String responseCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.responseCode = responseCode;
    }
}
