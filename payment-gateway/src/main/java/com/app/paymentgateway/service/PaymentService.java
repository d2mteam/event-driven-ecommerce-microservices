package com.app.paymentgateway.service;

import com.app.paymentgateway.config.PaymentMessagingProperties;
import com.app.paymentgateway.config.PaymentProperties;
import com.app.paymentgateway.dto.CompleteMockPaymentRequest;
import com.app.paymentgateway.dto.CreatePaymentRequest;
import com.app.paymentgateway.dto.PaymentResponse;
import com.app.paymentgateway.dto.VnpayIpnResponse;
import com.app.paymentgateway.entity.Payment;
import com.app.paymentgateway.entity.PaymentOutboxMessage;
import com.app.paymentgateway.event.EventVersions;
import com.app.paymentgateway.event.OrderCancellationRequestedEvent;
import com.app.paymentgateway.event.PaymentEventType;
import com.app.paymentgateway.event.PaymentResultEvent;
import com.app.paymentgateway.exception.PaymentConflictException;
import com.app.paymentgateway.exception.PaymentNotFoundException;
import com.app.paymentgateway.mapper.PaymentMapper;
import com.app.paymentgateway.model.PaymentOutboxStatus;
import com.app.paymentgateway.model.PaymentProviderType;
import com.app.paymentgateway.model.PaymentStatus;
import com.app.paymentgateway.provider.PaymentProvider;
import com.app.paymentgateway.provider.VnpayNotification;
import com.app.paymentgateway.repository.PaymentOutboxMessageRepository;
import com.app.paymentgateway.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentOutboxMessageRepository outboxRepository;
    private final PaymentProperties paymentProperties;
    private final PaymentMessagingProperties messagingProperties;
    private final ObjectMapper objectMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentProvider paymentProvider;

    public PaymentResponse create(CreatePaymentRequest request) {
        Optional<Payment> existing = paymentRepository.findByOrderId(
                request.orderId()
        );
        if (existing.isPresent()) {
            return responseForSameOrder(
                    existing.get(),
                    request.amount(),
                    request.clientIp()
            );
        }

        Instant createdAt = Instant.now();
        Payment payment = Payment.pending(
                request.orderId(),
                request.amount(),
                paymentProvider.type(),
                createdAt,
                createdAt.plus(paymentProperties.ttl())
        );

        try {
            Payment saved = paymentRepository.saveAndFlush(payment);
            return responseWithCheckoutUrl(saved, request.clientIp());
        } catch (DataIntegrityViolationException exception) {
            Payment winner = paymentRepository
                    .findByOrderId(request.orderId())
                    .orElseThrow(() -> exception);
            return responseForSameOrder(
                    winner,
                    request.amount(),
                    request.clientIp()
            );
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(Long paymentId) {
        return paymentMapper.toResponse(
                findPayment(paymentId),
                null
        );
    }

    @Transactional
    public PaymentResponse complete(
            Long paymentId,
            CompleteMockPaymentRequest request
    ) {
        Payment payment = paymentRepository
                .findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        if (payment.getProvider() != PaymentProviderType.MOCK) {
            throw new PaymentConflictException(
                    "Payment " + paymentId + " is not a mock payment"
            );
        }
        PaymentStatus result = paymentMapper.toStatus(request.result());

        if (payment.getStatus() == result) {
            return paymentMapper.toResponse(payment, null);
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
        return paymentMapper.toResponse(payment, null);
    }

    @Transactional
    public VnpayIpnResponse applyVnpayResult(
            VnpayNotification notification
    ) {
        Payment payment = paymentRepository
                .findByIdForUpdate(notification.paymentId())
                .orElse(null);
        if (payment == null || payment.getProvider() != PaymentProviderType.VNPAY) {
            return new VnpayIpnResponse(
                    "01",
                    "Payment not found"
            );
        }
        if (toVnpayAmount(payment.getAmount()) != notification.amount()) {
            return new VnpayIpnResponse(
                    "04",
                    "Invalid amount"
            );
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return new VnpayIpnResponse(
                    "02",
                    "Payment already confirmed"
            );
        }

        Instant completedAt = Instant.now();
        PaymentStatus result = notification.successful()
                ? PaymentStatus.SUCCEEDED
                : PaymentStatus.FAILED;
        payment.recordProviderResult(
                notification.transactionNo(),
                notification.responseCode(),
                notification.transactionStatus()
        );
        payment.complete(result, completedAt);
        saveResultEvent(payment, eventTypeFor(result), completedAt);
        return new VnpayIpnResponse(
                "00",
                "Confirm Success"
        );
    }

    @Transactional(readOnly = true)
    public UUID findOrderId(Long paymentId) {
        return findPayment(paymentId).getOrderId();
    }

    public int expirePendingPaymentsBatch(Instant expiredAt, int batchSize) {
        List<Payment> payments = paymentRepository.findExpiredForUpdate(
                PaymentStatus.PENDING.name(),
                expiredAt,
                batchSize
        );

        try {
            for (Payment payment : payments) {
                if (payment.expire(expiredAt)) {
                    saveResultEvent(
                            payment,
                            PaymentEventType.PAYMENT_EXPIRED,
                            expiredAt
                    );
                }
            }
        } catch (RuntimeException exception) {
            List<Long> claimedPaymentIds = payments.stream()
                    .map(Payment::getId)
                    .toList();
            log.error(
                    "Payment expiration batch failed cutoff={} paymentIds={}",
                    expiredAt,
                    claimedPaymentIds,
                    exception
            );
            throw exception;
        }
        return payments.size();
    }

    @Transactional
    public void refund(OrderCancellationRequestedEvent event) {
        Payment payment = paymentRepository
                .findByOrderIdForUpdate(event.orderId())
                .orElseThrow(() -> new PaymentConflictException(
                        "Order " + event.orderId() + " has no payment"
                ));

        if (payment.getAmount().compareTo(event.amount()) != 0) {
            throw new PaymentConflictException(
                    "Refund amount does not match payment " + payment.getId()
            );
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new PaymentConflictException(
                    "Payment " + payment.getId()
                            + " cannot be refunded from "
                            + payment.getStatus()
            );
        }

        payment.refund();
        saveResultEvent(
                payment,
                PaymentEventType.PAYMENT_REFUNDED,
                Instant.now()
        );
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private PaymentResponse responseForSameOrder(
            Payment payment,
            BigDecimal requestedAmount,
            String clientIp
    ) {
        if (payment.getAmount().compareTo(requestedAmount) != 0) {
            throw new PaymentConflictException(
                    "Order " + payment.getOrderId()
                            + " already has a payment with a different amount"
            );
        }
        if (payment.getProvider() != paymentProvider.type()) {
            throw new PaymentConflictException(
                    "Existing payment uses provider " + payment.getProvider()
            );
        }
        return responseWithCheckoutUrl(payment, clientIp);
    }

    private PaymentResponse responseWithCheckoutUrl(
            Payment payment,
            String clientIp
    ) {
        String paymentUrl = payment.getStatus() == PaymentStatus.PENDING
                ? paymentProvider.createPaymentUrl(payment, clientIp)
                : null;
        return paymentMapper.toResponse(payment, paymentUrl);
    }

    private long toVnpayAmount(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private PaymentEventType eventTypeFor(PaymentStatus status) {
        return switch (status) {
            case SUCCEEDED -> PaymentEventType.PAYMENT_SUCCEEDED;
            case FAILED -> PaymentEventType.PAYMENT_FAILED;
            case REFUNDED -> PaymentEventType.PAYMENT_REFUNDED;
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
                .status(PaymentOutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(occurredAt)
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

}
