package com.app.paymentgateway.mapper;

import com.app.paymentgateway.config.PaymentProperties;
import com.app.paymentgateway.dto.PaymentResponse;
import com.app.paymentgateway.entity.Payment;
import com.app.paymentgateway.model.MockPaymentResult;
import com.app.paymentgateway.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentMapperTest {

    private final PaymentMapper mapper = Mappers.getMapper(PaymentMapper.class);

    @Test
    void mapsPaymentAndBuildsMockUrlWithoutDoubleSlash() {
        UUID orderId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-08-10T04:00:00Z");
        Payment payment = mock(Payment.class);
        when(payment.getId()).thenReturn(42L);
        when(payment.getOrderId()).thenReturn(orderId);
        when(payment.getAmount()).thenReturn(new BigDecimal("199000.00"));
        when(payment.getStatus()).thenReturn(PaymentStatus.PENDING);
        when(payment.getExpiresAt()).thenReturn(expiresAt);

        PaymentResponse response = mapper.toResponse(
                payment,
                properties("http://localhost:8080/")
        );

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.amount()).isEqualByComparingTo("199000.00");
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.paymentUrl())
                .isEqualTo("http://localhost:8080/api/payments/42/mock");
    }

    @Test
    void mapsMockResultByEnumName() {
        assertThat(mapper.toStatus(MockPaymentResult.SUCCEEDED))
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(mapper.toStatus(MockPaymentResult.FAILED))
                .isEqualTo(PaymentStatus.FAILED);
    }

    private PaymentProperties properties(String publicBaseUrl) {
        return new PaymentProperties(
                Duration.ofMinutes(15),
                Duration.ofSeconds(30),
                publicBaseUrl,
                50,
                10
        );
    }
}
