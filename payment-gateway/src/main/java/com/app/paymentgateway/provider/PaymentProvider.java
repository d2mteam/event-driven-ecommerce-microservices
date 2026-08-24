package com.app.paymentgateway.provider;

import com.app.paymentgateway.entity.Payment;
import com.app.paymentgateway.model.PaymentProviderType;

public interface PaymentProvider {

    PaymentProviderType type();

    String createPaymentUrl(Payment payment, String clientIp);
}
