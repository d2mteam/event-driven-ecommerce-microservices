package com.app.order.service;

import com.app.order.config.OutboxRelayProperties;
import com.app.order.entity.OutboxMessage;
import com.app.order.repository.OutboxMessageRepository;
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
public class OutboxRelay {

    private final OutboxMessageRepository outboxMessageRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxRelayProperties properties;

    @Scheduled(
            initialDelayString = "${app.outbox.initial-delay}",
            fixedDelayString = "${app.outbox.fixed-delay}"
    )
    @Transactional
    public void publishUnpublishedMessages() {
        List<OutboxMessage> messages =
                outboxMessageRepository.findByPublishedAtIsNullOrderByIdAsc(
                        PageRequest.of(0, properties.getBatchSize())
                );

        for (OutboxMessage message : messages) {
            if (!publish(message)) {
                break;
            }
            message.markPublished(Instant.now());
        }
    }

    private boolean publish(OutboxMessage message) {
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
                    "Outbox relay interrupted for message {}",
                    message.getMessageId()
            );
            return false;
        } catch (Exception exception) {
            log.warn(
                    "Cannot publish outbox message {} to topic {}",
                    message.getMessageId(),
                    message.getTopic(),
                    exception
            );
            return false;
        }
    }
}
