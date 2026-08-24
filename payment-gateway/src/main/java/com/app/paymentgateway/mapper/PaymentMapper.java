package com.app.paymentgateway.mapper;

import com.app.paymentgateway.dto.PaymentResponse;
import com.app.paymentgateway.entity.Payment;
import com.app.paymentgateway.model.MockPaymentResult;
import com.app.paymentgateway.model.PaymentStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PaymentMapper {

    @Mapping(target = "paymentUrl", source = "paymentUrl")
    PaymentResponse toResponse(
            Payment payment,
            String paymentUrl
    );

    PaymentStatus toStatus(MockPaymentResult result);
}
