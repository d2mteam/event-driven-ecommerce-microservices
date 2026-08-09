package com.app.order.messaging;

import com.app.order.event.EventVersions;
import com.app.order.event.PaymentResultEvent;
import com.app.order.event.PaymentEventType;
import com.app.order.model.PaymentResultOutcome;
import com.app.order.service.OrderCancellationService;
import com.app.order.service.OrderPersistenceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final ObjectMapper objectMapper;
    private final OrderPersistenceService persistenceService;
    private final OrderCancellationService cancellationService;

    @KafkaListener(
            topics = "${app.kafka.topics.payment-events}",
            groupId = "${app.kafka.consumer-group-id}"
    )
    public void consume(String payload) {
        PaymentResultEvent event = readEvent(payload);
        validate(event);

        PaymentResultOutcome outcome = event.eventType()
                == PaymentEventType.PAYMENT_REFUNDED
                ? cancellationService.completeRefund(event)
                : persistenceService.applyPaymentResult(event);

        if (outcome == PaymentResultOutcome.INVARIANT_VIOLATION) {
            throw invalidEvent(
                    "Payment event conflicts with order state: "
                            + event.messageId()
            );
        }
        if (outcome == PaymentResultOutcome.DUPLICATE) {
            log.debug(
                    "Payment event {} was already applied to order {}",
                    event.messageId(),
                    event.orderId()
            );
        }
    }

    private PaymentResultEvent readEvent(String payload) {
        if (payload == null || payload.isBlank()) {
            throw invalidEvent("Payment event payload is empty");
        }
        try {
            return objectMapper.readValue(payload, PaymentResultEvent.class);
        } catch (JsonProcessingException exception) {
            throw new NonRetryableOrderEventException(
                    "Malformed payment event JSON",
                    exception
            );
        }
    }

    /** Field bắt buộc do compact constructor của record lo. Ở đây chỉ xét
     *  tương thích phiên bản — v2 không phải sai, chỉ là consumer chưa hiểu. */
    private void validate(PaymentResultEvent event) {
        if (event.eventVersion() != EventVersions.PAYMENT_RESULT) {
            throw invalidEvent(
                    "Unsupported payment event version: "
                            + event.eventVersion()
            );
        }
    }

    private NonRetryableOrderEventException invalidEvent(String message) {
        return new NonRetryableOrderEventException(message);
    }
}
