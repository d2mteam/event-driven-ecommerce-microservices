package com.app.order.messaging;

import com.app.order.event.EventVersions;
import com.app.order.event.PaymentResultEvent;
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
    public void consume(String payload) throws JsonProcessingException {
        PaymentResultEvent event = objectMapper.readValue(
                payload,
                PaymentResultEvent.class
        );

        if (event.eventVersion() != EventVersions.PAYMENT_RESULT
                || event.eventType() == null) {
            log.warn(
                    "Skip unsupported payment event {}",
                    event.messageId()
            );
            return;
        }
        if (event.messageId() == null
                || event.paymentId() == null
                || event.orderId() == null
                || event.amount() == null
                || event.occurredAt() == null) {
            log.warn("Skip incomplete payment event {}", event.messageId());
            return;
        }

        boolean changed = persistenceService.applyPaymentResult(event);
        if (!changed) {
            log.debug(
                    "Payment event {} did not change order {}",
                    event.messageId(),
                    event.orderId()
            );
        }
    }
}
