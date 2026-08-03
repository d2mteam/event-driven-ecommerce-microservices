package com.app.inventory.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.app.inventory.config.InventoryReservationProperties;
import com.app.inventory.service.InventoryReservationService;

class ExpiredReservationSweeperTest {

    private final InventoryReservationService reservationService =
            mock(InventoryReservationService.class);
    private final InventoryReservationProperties properties =
            mock(InventoryReservationProperties.class);
    private final PlatformTransactionManager transactionManager =
            mock(PlatformTransactionManager.class);

    private ExpiredReservationSweeper sweeper;

    @BeforeEach
    void setUp() {
        TransactionStatus transactionStatus = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        when(properties.batchSize()).thenReturn(2);
        when(properties.maxBatchesPerRun()).thenReturn(10);

        sweeper = new ExpiredReservationSweeper(
                reservationService,
                properties,
                new TransactionTemplate(transactionManager)
        );
    }

    @Test
    void drainsFullBatchesUntilTheLastPartialBatch() {
        when(reservationService.releaseExpiredReservations(any(), eq(2)))
                .thenReturn(2, 2, 1);

        sweeper.releaseExpiredReservations();

        verify(reservationService, times(3))
                .releaseExpiredReservations(any(), eq(2));
        verify(transactionManager, times(3)).commit(any());
    }

    @Test
    void stopsAtTheConfiguredBatchLimit() {
        when(properties.maxBatchesPerRun()).thenReturn(3);
        when(reservationService.releaseExpiredReservations(any(), eq(2)))
                .thenReturn(2);

        sweeper.releaseExpiredReservations();

        verify(reservationService, times(3))
                .releaseExpiredReservations(any(), eq(2));
        verify(transactionManager, times(3)).commit(any());
    }

    @Test
    void stopsTheDrainLoopWhenABatchFails() {
        when(reservationService.releaseExpiredReservations(any(), eq(2)))
                .thenThrow(new IllegalStateException("broken reservation"));

        sweeper.releaseExpiredReservations();

        verify(reservationService).releaseExpiredReservations(any(), eq(2));
        verify(transactionManager).rollback(any());
    }
}
