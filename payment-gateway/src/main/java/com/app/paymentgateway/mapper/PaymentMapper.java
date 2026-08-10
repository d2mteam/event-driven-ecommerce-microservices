package com.app.paymentgateway.mapper;

import com.app.paymentgateway.config.PaymentProperties;
import com.app.paymentgateway.dto.PaymentResponse;
import com.app.paymentgateway.entity.Payment;
import com.app.paymentgateway.model.MockPaymentResult;
import com.app.paymentgateway.model.PaymentStatus;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PaymentMapper {

    String MOCK_PAYMENT_PATH = "/api/payments/%d/mock";

    @Mapping(
            target = "paymentUrl",
            expression = "java(toPaymentUrl(payment.getId(), properties))"
    )
    PaymentResponse toResponse(
            Payment payment,
            @Context PaymentProperties properties
    );

    PaymentStatus toStatus(MockPaymentResult result);

    default String toPaymentUrl(
            Long paymentId,
            PaymentProperties properties
    ) {
        String baseUrl = properties.publicBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + MOCK_PAYMENT_PATH.formatted(paymentId);
    }
}
