package com.app.paymentgateway.scheduler;

import com.app.paymentgateway.config.PaymentProperties;
import com.app.paymentgateway.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentExpirationSweeperTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private PaymentExpirationSweeper sweeper;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(
                Duration.ofMinutes(15),
                Duration.ofSeconds(30),
                "http://localhost:8080",
                2,
                3
        );
        sweeper = new PaymentExpirationSweeper(
                paymentService,
                transactionTemplate,
                properties
        );

        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
    }

    @Test
    void drainsFullBatchesUntilTheLastPartialBatch() {
        when(paymentService.expirePendingPaymentsBatch(any(), eq(2)))
                .thenReturn(2, 2, 1);

        sweeper.expirePendingPayments();

        verify(paymentService, times(3))
                .expirePendingPaymentsBatch(any(), eq(2));
        verify(transactionTemplate, times(3)).execute(any());
    }

    @Test
    void stopsAtTheConfiguredBatchLimit() {
        when(paymentService.expirePendingPaymentsBatch(any(), eq(2)))
                .thenReturn(2);

        sweeper.expirePendingPayments();

        verify(paymentService, times(3))
                .expirePendingPaymentsBatch(any(), eq(2));
        verify(transactionTemplate, times(3)).execute(any());
    }
}
