package com.app.paymentgateway.provider;

import com.app.paymentgateway.config.PaymentProperties;
import com.app.paymentgateway.entity.Payment;
import com.app.paymentgateway.model.PaymentProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.payment.provider",
        havingValue = "MOCK",
        matchIfMissing = true
)
public class MockPaymentProvider implements PaymentProvider {

    private static final String PAYMENT_PATH = "/api/payments/%d/mock";

    private final PaymentProperties properties;

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.MOCK;
    }

    @Override
    public String createPaymentUrl(Payment payment, String clientIp) {
        return properties.publicBaseUrl().replaceAll("/+$", "")
                + PAYMENT_PATH.formatted(payment.getId());
    }

    @Override
    public PaymentRefundResult refund(Payment payment, UUID requestId) {
        return PaymentRefundResult.success();
    }
}
