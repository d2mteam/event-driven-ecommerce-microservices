package com.app.notification.messaging;

import com.app.notification.event.EventVersions;
import com.app.notification.event.OrderConfirmedEvent;
import com.app.notification.event.OrderEventType;
import com.app.notification.event.OrderItem;
import com.app.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.kafka.retry.max-attempts=10",
                "app.kafka.retry.initial-interval=10ms",
                "app.kafka.retry.multiplier=1.0",
                "app.kafka.retry.max-interval=10ms",
                "app.kafka.retry.partitions=1",
                "app.kafka.retry.replication-factor=1"
        }
)
@Import(OrderEventDltIntegrationTest.TopicConfiguration.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderEventDltIntegrationTest {

    private static final String SOURCE_TOPIC = "order.events";
    private static final String DLT_TOPIC =
            "order.events.notification-service.DLT";

    @Container
    @ServiceConnection
    static final MariaDBContainer<?> MARIA_DB =
            new MariaDBContainer<>("mariadb:11.4.10");

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA =
            new KafkaContainer("apache/kafka:4.3.1");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoSpyBean
    private OrderEventListener orderEventListener;

    @Test
    void malformedEventGoesStraightToDltWithSourceMetadata() throws Exception {
        String key = "malformed-" + UUID.randomUUID();
        String payload = "{not-json";

        RecordMetadata source = send(key, payload);
        ConsumerRecord<String, String> deadLetter = awaitDlt(key);

        assertThat(deadLetter.key()).isEqualTo(key);
        assertThat(deadLetter.value()).isEqualTo(payload);
        assertThat(textHeader(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC))
                .isEqualTo(SOURCE_TOPIC);
        assertThat(intHeader(deadLetter, KafkaHeaders.DLT_ORIGINAL_PARTITION))
                .isEqualTo(source.partition());
        assertThat(longHeader(deadLetter, KafkaHeaders.DLT_ORIGINAL_OFFSET))
                .isEqualTo(source.offset());
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_FQCN))
                .isNotNull();
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_MESSAGE))
                .isNotNull();
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_STACKTRACE))
                .isNotNull();

        verify(orderEventListener, times(1)).consume(payload);
        verifyNoInteractions(notificationService);
    }

    @Test
    void retryableFailureRunsTenTimesThenGoesToDlt() throws Exception {
        UUID orderId = UUID.randomUUID();
        String key = "retryable-" + orderId;
        String payload = objectMapper.writeValueAsString(confirmedEvent(orderId));
        doThrow(new IllegalStateException("database unavailable"))
                .when(notificationService)
                .createSuccessFor(any(OrderConfirmedEvent.class));

        send(key, payload);
        ConsumerRecord<String, String> deadLetter = awaitDlt(key);

        assertThat(deadLetter.key()).isEqualTo(key);
        assertThat(textHeader(
                deadLetter,
                KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN
        )).contains(IllegalStateException.class.getName());
        verify(notificationService, times(10)).createSuccessFor(argThat(
                event -> orderId.equals(event.orderId())
        ));
    }

    @Test
    void successBeforeLastAttemptDoesNotGoToDlt() throws Exception {
        UUID orderId = UUID.randomUUID();
        String key = "eventual-success-" + orderId;
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("temporary database failure");
            }
            return null;
        }).when(notificationService).createSuccessFor(any(OrderConfirmedEvent.class));

        send(key, objectMapper.writeValueAsString(confirmedEvent(orderId)));

        verify(notificationService, org.mockito.Mockito.timeout(5_000).times(3))
                .createSuccessFor(argThat(event -> orderId.equals(event.orderId())));
        assertThat(findDlt(key, Duration.ofSeconds(1))).isNull();
    }

    private RecordMetadata send(String key, String payload) throws Exception {
        SendResult<String, String> result = kafkaTemplate
                .send(SOURCE_TOPIC, 0, key, payload)
                .get(10, TimeUnit.SECONDS);
        return result.getRecordMetadata();
    }

    private ConsumerRecord<String, String> awaitDlt(String key) {
        ConsumerRecord<String, String> record =
                findDlt(key, Duration.ofSeconds(15));
        assertThat(record)
                .as("DLT record with key %s", key)
                .isNotNull();
        return record;
    }

    private ConsumerRecord<String, String> findDlt(
            String expectedKey,
            Duration timeout
    ) {
        try (KafkaConsumer<String, String> consumer = dltConsumer()) {
            consumer.subscribe(List.of(DLT_TOPIC));
            long deadline = System.nanoTime() + timeout.toNanos();

            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records =
                        consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    if (expectedKey.equals(record.key())) {
                        return record;
                    }
                }
            }
            return null;
        }
    }

    private KafkaConsumer<String, String> dltConsumer() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()
        );
        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "dlt-assertion-" + UUID.randomUUID()
        );
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );
        return new KafkaConsumer<>(properties);
    }

    private byte[] header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : header.value();
    }

    private String textHeader(
            ConsumerRecord<String, String> record,
            String name
    ) {
        return new String(header(record, name), StandardCharsets.UTF_8);
    }

    private int intHeader(ConsumerRecord<String, String> record, String name) {
        return ByteBuffer.wrap(header(record, name)).getInt();
    }

    private long longHeader(
            ConsumerRecord<String, String> record,
            String name
    ) {
        return ByteBuffer.wrap(header(record, name)).getLong();
    }

    private OrderConfirmedEvent confirmedEvent(UUID orderId) {
        return new OrderConfirmedEvent(
                UUID.randomUUID(),
                EventVersions.ORDER_CONFIRMED,
                OrderEventType.ORDER_CONFIRMED,
                orderId,
                UUID.randomUUID(),
                42L,
                new BigDecimal("199000.00"),
                List.of(new OrderItem(
                        7L,
                        "Mechanical Keyboard",
                        new BigDecimal("199000.00"),
                        1
                )),
                Instant.now()
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TopicConfiguration {

        @Bean
        org.apache.kafka.clients.admin.NewTopic orderEventsTopic() {
            return TopicBuilder.name(SOURCE_TOPIC)
                    .partitions(1)
                    .replicas(1)
                    .build();
        }
    }
}
