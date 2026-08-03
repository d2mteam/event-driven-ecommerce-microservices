package com.app.paymentgateway.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long paymentId) {
        super("Payment " + paymentId + " was not found");
    }
}
