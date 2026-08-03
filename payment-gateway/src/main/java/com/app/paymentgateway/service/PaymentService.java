package com.app.paymentgateway.service;

import com.app.paymentgateway.config.PaymentMessagingProperties;
import com.app.paymentgateway.config.PaymentProperties;
import com.app.paymentgateway.dto.CompleteMockPaymentRequest;
import com.app.paymentgateway.dto.CreatePaymentRequest;
import com.app.paymentgateway.dto.PaymentResponse;
import com.app.paymentgateway.entity.Payment;
import com.app.paymentgateway.entity.PaymentOutboxMessage;
import com.app.paymentgateway.event.EventVersions;
import com.app.paymentgateway.event.PaymentEventType;
import com.app.paymentgateway.event.PaymentResultEvent;
import com.app.paymentgateway.exception.PaymentConflictException;
import com.app.paymentgateway.exception.PaymentNotFoundException;
import com.app.paymentgateway.model.MockPaymentResult;
import com.app.paymentgateway.model.PaymentStatus;
import com.app.paymentgateway.repository.PaymentOutboxMessageRepository;
import com.app.paymentgateway.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String PAYMENT_PATH = "/api/payments/";

    private final PaymentRepository paymentRepository;
    private final PaymentOutboxMessageRepository outboxRepository;
    private final PaymentProperties paymentProperties;
    private final PaymentMessagingProperties messagingProperties;
    private final ObjectMapper objectMapper;

    public PaymentResponse create(CreatePaymentRequest request) {
        Optional<Payment> existing = paymentRepository.findByOrderId(
                request.orderId()
        );
        if (existing.isPresent()) {
            return responseForSameOrder(existing.get(), request.amount());
        }

        Instant createdAt = Instant.now();
        Payment payment = Payment.pending(
                request.orderId(),
                request.amount(),
                createdAt,
                createdAt.plus(paymentProperties.ttl())
        );

        try {
            return toResponse(paymentRepository.saveAndFlush(payment));
        } catch (DataIntegrityViolationException exception) {
            Payment winner = paymentRepository
                    .findByOrderId(request.orderId())
                    .orElseThrow(() -> exception);
            return responseForSameOrder(winner, request.amount());
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(Long paymentId) {
        return toResponse(findPayment(paymentId));
    }

    @Transactional
    public PaymentResponse complete(
            Long paymentId,
            CompleteMockPaymentRequest request
    ) {
        Payment payment = paymentRepository
                .findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        PaymentStatus result = toStatus(request.result());

        if (payment.getStatus() == result) {
            return toResponse(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw terminalConflict(payment);
        }

        Instant completedAt = Instant.now();
        if (!completedAt.isBefore(payment.getExpiresAt())) {
            throw new PaymentConflictException(
                    "Payment " + paymentId + " has expired"
            );
        }

        payment.complete(result, completedAt);
        saveResultEvent(payment, eventTypeFor(result), completedAt);
        return toResponse(payment);
    }

    @Transactional
    public int expirePendingPayments() {
        Instant expiredAt = Instant.now();
        List<Payment> payments = paymentRepository.findExpiredForUpdate(
                PaymentStatus.PENDING,
                expiredAt
        );

        for (Payment payment : payments) {
            if (payment.expire(expiredAt)) {
                saveResultEvent(
                        payment,
                        PaymentEventType.PAYMENT_EXPIRED,
                        expiredAt
                );
            }
        }
        return payments.size();
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private PaymentResponse responseForSameOrder(
            Payment payment,
            BigDecimal requestedAmount
    ) {
        if (payment.getAmount().compareTo(requestedAmount) != 0) {
            throw new PaymentConflictException(
                    "Order " + payment.getOrderId()
                            + " already has a payment with a different amount"
            );
        }
        return toResponse(payment);
    }

    private PaymentStatus toStatus(MockPaymentResult result) {
        return switch (result) {
            case SUCCEEDED -> PaymentStatus.SUCCEEDED;
            case FAILED -> PaymentStatus.FAILED;
        };
    }

    private PaymentEventType eventTypeFor(PaymentStatus status) {
        return switch (status) {
            case SUCCEEDED -> PaymentEventType.PAYMENT_SUCCEEDED;
            case FAILED -> PaymentEventType.PAYMENT_FAILED;
            default -> throw new IllegalArgumentException(
                    "No completion event for payment status " + status
            );
        };
    }

    private PaymentConflictException terminalConflict(Payment payment) {
        return new PaymentConflictException(
                "Payment " + payment.getId()
                        + " is already " + payment.getStatus()
        );
    }

    private void saveResultEvent(
            Payment payment,
            PaymentEventType eventType,
            Instant occurredAt
    ) {
        PaymentResultEvent event = new PaymentResultEvent(
                UUID.randomUUID(),
                EventVersions.PAYMENT_RESULT,
                eventType,
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                occurredAt
        );
        outboxRepository.save(PaymentOutboxMessage.builder()
                .messageId(event.messageId())
                .topic(messagingProperties.getTopics().getPaymentEvents())
                .key(event.orderId().toString())
                .type(event.eventType().name())
                .payload(serialize(event))
                .createdAt(occurredAt)
                .build());
    }

    private String serialize(PaymentResultEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Cannot serialize PaymentResultEvent",
                    exception
            );
        }
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getExpiresAt(),
                paymentUrl(payment.getId())
        );
    }

    private String paymentUrl(Long paymentId) {
        String baseUrl = paymentProperties.publicBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + PAYMENT_PATH + paymentId + "/mock";
    }
}
