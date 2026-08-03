package com.app.order.messaging;

import com.app.order.event.EventVersions;
import com.app.order.event.PaymentResultEvent;
import com.app.order.model.PaymentResultOutcome;
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

    @KafkaListener(
            topics = "${app.kafka.topics.payment-events}",
            groupId = "${app.kafka.consumer-group-id}"
    )
    public void consume(String payload) {
        PaymentResultEvent event = readEvent(payload);
        validate(event);

        PaymentResultOutcome outcome =
                persistenceService.applyPaymentResult(event);

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

    private void validate(PaymentResultEvent event) {
        if (event == null) {
            throw invalidEvent("Payment event must be a JSON object");
        }
        if (event.eventVersion() != EventVersions.PAYMENT_RESULT) {
            throw invalidEvent(
                    "Unsupported payment event version: "
                            + event.eventVersion()
            );
        }
        if (event.messageId() == null
                || event.eventType() == null
                || event.paymentId() == null
                || event.orderId() == null
                || event.amount() == null
                || event.occurredAt() == null) {
            throw invalidEvent("Payment event is missing required fields");
        }
    }

    private NonRetryableOrderEventException invalidEvent(String message) {
        return new NonRetryableOrderEventException(message);
    }
}
