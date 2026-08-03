package com.app.paymentgateway.scheduler;

import com.app.paymentgateway.config.PaymentProperties;
import com.app.paymentgateway.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpirationSweeper {

    private final PaymentService paymentService;
    private final TransactionTemplate transactionTemplate;
    private final PaymentProperties properties;

    @Scheduled(fixedDelayString = "${app.payment.sweep-delay}")
    public void expirePendingPayments() {
        Instant cutoff = Instant.now();
        int totalExpired = 0;

        try {
            for (int batch = 0;
                 batch < properties.maxSweepBatchesPerRun();
                 batch++) {
                Integer expired = transactionTemplate.execute(ignored ->
                        paymentService.expirePendingPaymentsBatch(
                                cutoff,
                                properties.sweepBatchSize()
                        )
                );
                if (expired == null) {
                    throw new IllegalStateException(
                            "Payment expiration transaction returned no result"
                    );
                }

                totalExpired += expired;
                if (expired < properties.sweepBatchSize()) {
                    break;
                }
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Payment expiration job failed cutoff={}",
                    cutoff,
                    exception
            );
            return;
        }

        if (totalExpired > 0) {
            log.info("Expired {} pending payments", totalExpired);
        }
    }
}
