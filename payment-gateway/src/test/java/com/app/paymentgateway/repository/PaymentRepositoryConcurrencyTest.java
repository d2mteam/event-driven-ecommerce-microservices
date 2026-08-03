package com.app.paymentgateway.repository;

import com.app.paymentgateway.entity.Payment;
import com.app.paymentgateway.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class PaymentRepositoryConcurrencyTest {

    @Container
    static final MariaDBContainer<?> MARIA_DB =
            new MariaDBContainer<>("mariadb:11.4.10");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIA_DB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIA_DB::getUsername);
        registry.add("spring.datasource.password", MARIA_DB::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentWorkersNeverClaimTheSamePayment() throws Exception {
        Instant cutoff = Instant.parse("2026-08-04T03:00:00Z");
        paymentRepository.saveAllAndFlush(List.of(
                pendingPayment(cutoff.minusSeconds(40)),
                pendingPayment(cutoff.minusSeconds(30)),
                pendingPayment(cutoff.minusSeconds(20)),
                pendingPayment(cutoff.minusSeconds(10))
        ));

        CountDownLatch bothWorkersClaimed = new CountDownLatch(2);
        CountDownLatch releaseTransactions = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);

        try {
            Future<List<Long>> first = workers.submit(() -> claimBatch(
                    cutoff,
                    bothWorkersClaimed,
                    releaseTransactions
            ));
            Future<List<Long>> second = workers.submit(() -> claimBatch(
                    cutoff,
                    bothWorkersClaimed,
                    releaseTransactions
            ));

            assertThat(bothWorkersClaimed.await(20, TimeUnit.SECONDS))
                    .isTrue();
            releaseTransactions.countDown();

            List<Long> firstIds = first.get(20, TimeUnit.SECONDS);
            List<Long> secondIds = second.get(20, TimeUnit.SECONDS);
            Set<Long> allClaimedIds = new HashSet<>(firstIds);
            allClaimedIds.addAll(secondIds);

            assertThat(firstIds).hasSizeLessThanOrEqualTo(2);
            assertThat(secondIds).hasSizeLessThanOrEqualTo(2);
            assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
            assertThat(allClaimedIds).isNotEmpty();
        } finally {
            releaseTransactions.countDown();
            workers.shutdownNow();
        }
    }

    private List<Long> claimBatch(
            Instant cutoff,
            CountDownLatch bothWorkersClaimed,
            CountDownLatch releaseTransactions
    ) {
        return transactionTemplate.execute(status -> {
            List<Long> paymentIds = paymentRepository.findExpiredForUpdate(
                            PaymentStatus.PENDING.name(),
                            cutoff,
                            2
                    ).stream()
                    .map(Payment::getId)
                    .toList();
            bothWorkersClaimed.countDown();
            await(releaseTransactions);
            return paymentIds;
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for worker");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Worker was interrupted", exception);
        }
    }

    private Payment pendingPayment(Instant expiresAt) {
        return Payment.pending(
                UUID.randomUUID(),
                BigDecimal.valueOf(100_000),
                expiresAt.minusSeconds(900),
                expiresAt
        );
    }
}
