package com.app.inventory.scheduler;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.app.inventory.config.InventoryReservationProperties;
import com.app.inventory.service.InventoryReservationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredReservationSweeper {

    private final InventoryReservationService reservationService;
    private final InventoryReservationProperties properties;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${app.inventory.reservation.sweep-delay}")
    public void releaseExpiredReservations() {
        Instant expiresAt = Instant.now();
        int totalReleased = 0;

        for (int batchNumber = 1;
                batchNumber <= properties.maxBatchesPerRun();
                batchNumber++) {
            int releasedCount;
            try {
                releasedCount = transactionTemplate.execute(status ->
                        reservationService.releaseExpiredReservations(
                                expiresAt,
                                properties.batchSize()
                        )
                );
            } catch (RuntimeException exception) {
                log.error(
                        "Inventory expiration batch failed: batch={}, expiresAt={}",
                        batchNumber,
                        expiresAt,
                        exception
                );
                return;
            }

            totalReleased += releasedCount;
            if (releasedCount < properties.batchSize()) {
                break;
            }
        }

        if (totalReleased > 0) {
            log.info("Released {} expired inventory reservation(s)", totalReleased);
        }
    }
}
