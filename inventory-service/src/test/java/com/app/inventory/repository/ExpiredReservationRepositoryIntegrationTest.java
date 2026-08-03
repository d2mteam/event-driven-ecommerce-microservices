package com.app.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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

import com.app.inventory.config.InventoryMessagingProperties;
import com.app.inventory.config.InventoryReservationProperties;
import com.app.inventory.entity.Inventory;
import com.app.inventory.entity.InventoryReservation;
import com.app.inventory.entity.ReservationItem;
import com.app.inventory.entity.ReservationStatus;
import com.app.inventory.mapper.InventoryReservationMapper;
import com.app.inventory.service.InventoryOutboxWriter;
import com.app.inventory.service.impl.InventoryReservationServiceImpl;
import com.fasterxml.jackson.databind.json.JsonMapper;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ExpiredReservationRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Container
    @ServiceConnection
    static final MariaDBContainer<?> MARIA_DB =
            new MariaDBContainer<>("mariadb:11.4.10");

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Autowired
    private InventoryOutboxMessageRepository outboxRepository;

    private TransactionTemplate transaction;
    private InventoryReservationServiceImpl reservationService;

    @Autowired
    void setTransactionManager(PlatformTransactionManager transactionManager) {
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    @BeforeEach
    void setUp() {
        transaction.executeWithoutResult(status -> {
            outboxRepository.deleteAll();
            reservationRepository.deleteAll();
            inventoryRepository.deleteAll();
        });

        InventoryOutboxWriter outboxWriter = new InventoryOutboxWriter(
                outboxRepository,
                new InventoryMessagingProperties("inventory.events"),
                JsonMapper.builder().findAndAddModules().build()
        );
        reservationService = new InventoryReservationServiceImpl(
                inventoryRepository,
                reservationRepository,
                mock(InventoryReservationProperties.class),
                mock(InventoryReservationMapper.class),
                outboxWriter
        );
    }

    @Test
    void limitsExpiredReservationsToTheRequestedBatchSize() {
        saveExpiredReservation(101L, 1);
        saveExpiredReservation(102L, 1);
        saveExpiredReservation(103L, 1);

        List<InventoryReservation> claimed = inTransaction(() ->
                reservationRepository.findExpiredForUpdate(
                        ReservationStatus.HELD.name(),
                        NOW,
                        2
                )
        );

        assertThat(claimed).hasSize(2);
    }

    @Test
    void concurrentWorkersNeverClaimTheSameReservation() throws Exception {
        saveExpiredReservation(201L, 1);
        saveExpiredReservation(202L, 1);
        saveExpiredReservation(203L, 1);
        saveExpiredReservation(204L, 1);

        CountDownLatch firstWorkerHasLocks = new CountDownLatch(1);
        CountDownLatch releaseFirstWorker = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);

        try {
            Future<List<Long>> firstWorker = workers.submit(() ->
                    claimAndHold(firstWorkerHasLocks, releaseFirstWorker)
            );

            assertThat(firstWorkerHasLocks.await(5, TimeUnit.SECONDS)).isTrue();

            Future<List<Long>> secondWorker = workers.submit(this::claimExpired);
            List<Long> secondWorkerIds = secondWorker.get(5, TimeUnit.SECONDS);

            releaseFirstWorker.countDown();
            List<Long> firstWorkerIds = firstWorker.get(5, TimeUnit.SECONDS);

            Set<Long> overlap = new HashSet<>(firstWorkerIds);
            overlap.retainAll(secondWorkerIds);

            assertThat(firstWorkerIds).hasSizeLessThanOrEqualTo(2);
            assertThat(secondWorkerIds).hasSizeLessThanOrEqualTo(2);
            assertThat(overlap).isEmpty();
        } finally {
            releaseFirstWorker.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void expirationReleasesInventoryAndWritesOneOutboxMessageOnlyOnce() {
        Long reservationId = saveExpiredReservation(301L, 3);

        int firstRun = inTransaction(() ->
                reservationService.releaseExpiredReservations(NOW, 10)
        );
        int secondRun = inTransaction(() ->
                reservationService.releaseExpiredReservations(NOW, 10)
        );

        Inventory inventory = inventoryRepository.findAll().getFirst();
        InventoryReservation reservation = reservationRepository
                .findById(reservationId)
                .orElseThrow();

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isZero();
        assertThat(inventory.getReservedQuantity()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    private List<Long> claimAndHold(
            CountDownLatch claimed,
            CountDownLatch release
    ) {
        return inTransaction(() -> {
            List<Long> ids = findExpiredIds();
            claimed.countDown();
            await(release);
            return ids;
        });
    }

    private List<Long> claimExpired() {
        return inTransaction(this::findExpiredIds);
    }

    private List<Long> findExpiredIds() {
        return reservationRepository.findExpiredForUpdate(
                        ReservationStatus.HELD.name(),
                        NOW,
                        2
                ).stream()
                .map(InventoryReservation::getId)
                .toList();
    }

    private Long saveExpiredReservation(Long productId, int quantity) {
        return inTransaction(() -> {
            inventoryRepository.save(Inventory.builder()
                    .productId(productId)
                    .onHandQuantity(20)
                    .reservedQuantity(quantity)
                    .build());

            InventoryReservation reservation = InventoryReservation.held(
                    UUID.randomUUID(),
                    List.of(new ReservationItem(productId, quantity)),
                    NOW.minusSeconds(120),
                    NOW.minusSeconds(60)
            );
            return reservationRepository.saveAndFlush(reservation).getId();
        });
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
