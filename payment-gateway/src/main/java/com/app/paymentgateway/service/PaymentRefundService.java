package com.app.paymentgateway.service;

import com.app.paymentgateway.entity.Payment;
import com.app.paymentgateway.event.OrderCancellationRequestedEvent;
import com.app.paymentgateway.exception.PaymentConflictException;
import com.app.paymentgateway.model.PaymentStatus;
import com.app.paymentgateway.provider.PaymentProvider;
import com.app.paymentgateway.provider.PaymentRefundResult;
import com.app.paymentgateway.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentRefundService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final PaymentService paymentService;

    public void refund(OrderCancellationRequestedEvent event) {
        Payment payment = paymentRepository.findByOrderId(event.orderId())
                .orElseThrow(() -> new PaymentConflictException(
                        "Order " + event.orderId() + " has no payment"
                ));

        validate(payment, event);
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }

        PaymentRefundResult result = paymentProvider.refund(
                payment,
                event.messageId()
        );
        if (result.successful()) {
            paymentService.completeRefund(event);
            return;
        }
        if (result.retryable()) {
            throw new IllegalStateException(result.message());
        }
        throw new PaymentConflictException(result.message());
    }

    private void validate(
            Payment payment,
            OrderCancellationRequestedEvent event
    ) {
        if (payment.getProvider() != paymentProvider.type()) {
            throw new PaymentConflictException(
                    "Payment provider is " + payment.getProvider()
            );
        }
        if (payment.getAmount().compareTo(event.amount()) != 0) {
            throw new PaymentConflictException(
                    "Refund amount does not match payment " + payment.getId()
            );
        }
        if (payment.getStatus() != PaymentStatus.SUCCEEDED
                && payment.getStatus() != PaymentStatus.REFUNDED) {
            throw new PaymentConflictException(
                    "Payment " + payment.getId()
                            + " cannot be refunded from "
                            + payment.getStatus()
            );
        }
    }
}
