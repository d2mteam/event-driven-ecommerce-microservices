package com.app.paymentgateway.service;

import com.app.paymentgateway.config.PaymentOutboxProperties;
import com.app.paymentgateway.entity.PaymentOutboxMessage;
import com.app.paymentgateway.repository.PaymentOutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOutboxRelay {

    private final PaymentOutboxMessageRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PaymentOutboxProperties properties;

    @Scheduled(
            initialDelayString = "${app.outbox.initial-delay}",
            fixedDelayString = "${app.outbox.fixed-delay}"
    )
    @Transactional
    public void publishUnpublishedMessages() {
        List<PaymentOutboxMessage> messages = outboxRepository
                .findByPublishedAtIsNullOrderByIdAsc(
                        PageRequest.of(0, properties.getBatchSize())
                );

        for (PaymentOutboxMessage message : messages) {
            if (!publish(message)) {
                break;
            }
            message.markPublished(Instant.now());
        }
    }

    private boolean publish(PaymentOutboxMessage message) {
        try {
            kafkaTemplate.send(
                            message.getTopic(),
                            message.getKey(),
                            message.getPayload()
                    )
                    .get(
                            properties.getSendTimeout().toMillis(),
                            TimeUnit.MILLISECONDS
                    );
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn(
                    "Payment outbox relay interrupted for message {}",
                    message.getMessageId()
            );
            return false;
        } catch (Exception exception) {
            log.warn(
                    "Cannot publish payment outbox message {} to {}",
                    message.getMessageId(),
                    message.getTopic(),
                    exception
            );
            return false;
        }
    }
}
