package com.app.inventory.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.app.inventory.service.InventoryReservationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredReservationSweeper {

    private final InventoryReservationService reservationService;

    @Scheduled(fixedDelayString = "${app.inventory.reservation.sweep-delay}")
    public void releaseExpiredReservations() {
        int releasedCount = reservationService.releaseExpiredReservations();
        if (releasedCount > 0) {
            log.info("Released {} expired inventory reservation(s)", releasedCount);
        }
    }
}
