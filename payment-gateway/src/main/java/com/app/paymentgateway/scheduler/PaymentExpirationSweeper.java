package com.app.paymentgateway.scheduler;

import com.app.paymentgateway.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpirationSweeper {

    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${app.payment.sweep-delay}")
    public void expirePendingPayments() {
        int expired = paymentService.expirePendingPayments();
        if (expired > 0) {
            log.info("Expired {} pending payments", expired);
        }
    }
}
