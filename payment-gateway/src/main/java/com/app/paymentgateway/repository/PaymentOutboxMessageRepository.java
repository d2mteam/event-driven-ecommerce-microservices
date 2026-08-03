package com.app.paymentgateway.repository;

import com.app.paymentgateway.entity.PaymentOutboxMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentOutboxMessageRepository
        extends JpaRepository<PaymentOutboxMessage, Long> {

    List<PaymentOutboxMessage> findByPublishedAtIsNullOrderByIdAsc(
            Pageable pageable
    );
}
