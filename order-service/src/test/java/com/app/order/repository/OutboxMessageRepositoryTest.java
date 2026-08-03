package com.app.order.repository;

import com.app.order.entity.OutboxMessage;
import com.app.order.model.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OutboxMessageRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Container
    @ServiceConnection
    static final MariaDBContainer<?> MARIA_DB =
            new MariaDBContainer<>("mariadb:11.4.10");

    @Autowired
    private OutboxMessageRepository repository;

    private TransactionTemplate transaction;

    @Autowired
    void setTransactionManager(PlatformTransactionManager transactionManager) {
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    @BeforeEach
    void clearOutbox() {
        transaction.executeWithoutResult(status -> repository.deleteAll());
    }

    @Test
    void limitsClaimedMessagesToRequestedBatchSize() {
        savePending("order-1");
        savePending("order-2");
        savePending("order-3");

        List<OutboxMessage> claimed = claimable(2);

        assertThat(claimed).hasSize(2);
    }

    @Test
    void staleProcessingLeaseBecomesClaimable() {
        OutboxMessage stale = save(message(
                "stale-order",
                OutboxStatus.PROCESSING,
                NOW.minusSeconds(1),
                "old-worker"
        ));
        OutboxMessage active = save(message(
                "active-order",
                OutboxStatus.PROCESSING,
                NOW.plusSeconds(60),
                "active-worker"
        ));

        List<Long> claimedIds = claimable(10).stream()
                .map(OutboxMessage::getId)
                .toList();

        assertThat(claimedIds)
                .contains(stale.getId())
                .doesNotContain(active.getId());
    }

    @Test
    void olderUnpublishedMessageBlocksLaterMessageWithSameKey() {
        OutboxMessage older = savePending("same-order");
        OutboxMessage later = savePending("same-order");

        List<Long> claimedIds = claimable(10).stream()
                .map(OutboxMessage::getId)
                .toList();

        assertThat(claimedIds).containsExactly(older.getId());
        assertThat(claimedIds).doesNotContain(later.getId());
    }

    @Test
    void failedMessageOnlyBlocksItsOwnKey() {
        save(message("blocked-order", OutboxStatus.FAILED, null, null));
        OutboxMessage blocked = savePending("blocked-order");
        OutboxMessage independent = savePending("other-order");

        List<Long> claimedIds = claimable(10).stream()
                .map(OutboxMessage::getId)
                .toList();

        assertThat(claimedIds)
                .containsExactly(independent.getId())
                .doesNotContain(blocked.getId());
    }

    @Test
    void concurrentWorkersNeverClaimTheSameMessage() throws Exception {
        savePending("order-1");
        savePending("order-2");
        savePending("order-3");
        savePending("order-4");

        CountDownLatch firstWorkerHasLocks = new CountDownLatch(1);
        CountDownLatch releaseFirstWorker = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);

        try {
            Future<List<Long>> firstWorker = workers.submit(() ->
                    claimAndHold(
                            "worker-one",
                            firstWorkerHasLocks,
                            releaseFirstWorker
                    )
            );

            assertThat(firstWorkerHasLocks.await(5, TimeUnit.SECONDS)).isTrue();

            Future<List<Long>> secondWorker = workers.submit(() ->
                    claim("worker-two")
            );
            List<Long> secondIds = secondWorker.get(5, TimeUnit.SECONDS);

            releaseFirstWorker.countDown();
            List<Long> firstIds = firstWorker.get(5, TimeUnit.SECONDS);

            Set<Long> overlap = new HashSet<>(firstIds);
            overlap.retainAll(secondIds);

            assertThat(firstIds).hasSizeLessThanOrEqualTo(2);
            assertThat(secondIds).hasSizeLessThanOrEqualTo(2);
            assertThat(overlap).isEmpty();
        } finally {
            releaseFirstWorker.countDown();
            workers.shutdownNow();
        }
    }

    private List<Long> claimAndHold(
            String workerToken,
            CountDownLatch claimed,
            CountDownLatch release
    ) {
        return inTransaction(() -> {
            List<OutboxMessage> messages =
                    repository.findClaimableForUpdate(NOW, 2);
            messages.forEach(message ->
                    message.claim(workerToken, NOW.plusSeconds(60))
            );
            repository.flush();
            claimed.countDown();
            await(release);
            return idsOf(messages);
        });
    }

    private List<Long> claim(String workerToken) {
        return inTransaction(() -> {
            List<OutboxMessage> messages =
                    repository.findClaimableForUpdate(NOW, 2);
            messages.forEach(message ->
                    message.claim(workerToken, NOW.plusSeconds(60))
            );
            repository.flush();
            return idsOf(messages);
        });
    }

    private List<OutboxMessage> claimable(int batchSize) {
        return inTransaction(() ->
                repository.findClaimableForUpdate(NOW, batchSize)
        );
    }

    private OutboxMessage savePending(String key) {
        return save(message(key, OutboxStatus.PENDING, null, null));
    }

    private OutboxMessage save(OutboxMessage message) {
        return inTransaction(() -> repository.saveAndFlush(message));
    }

    private OutboxMessage message(
            String key,
            OutboxStatus status,
            Instant lockedUntil,
            String lockToken
    ) {
        return OutboxMessage.builder()
                .messageId(UUID.randomUUID())
                .topic("order.events")
                .key(key)
                .type("OrderConfirmedEvent")
                .payload("{}")
                .createdAt(NOW.minusSeconds(60))
                .status(status)
                .attemptCount(status == OutboxStatus.PENDING ? 0 : 1)
                .nextAttemptAt(NOW.minusSeconds(1))
                .lockToken(lockToken)
                .lockedUntil(lockedUntil)
                .lastError(status == OutboxStatus.FAILED ? "invalid event" : null)
                .build();
    }

    private List<Long> idsOf(List<OutboxMessage> messages) {
        return messages.stream().map(OutboxMessage::getId).toList();
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transaction.execute(status -> work.get());
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for worker");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Worker was interrupted", exception);
        }
    }
}
