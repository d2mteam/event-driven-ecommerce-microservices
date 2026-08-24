package com.app.paymentgateway.provider;

import com.app.paymentgateway.entity.Payment;
import com.app.paymentgateway.model.PaymentProviderType;

import java.util.UUID;

public interface PaymentProvider {

    PaymentProviderType type();

    String createPaymentUrl(Payment payment, String clientIp);

    PaymentRefundResult refund(Payment payment, UUID requestId);
}
