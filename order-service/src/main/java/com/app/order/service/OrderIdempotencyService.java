package com.app.order.service;

import com.app.order.entity.OrderIdempotency;
import com.app.order.model.IdempotencyStatus;
import com.app.order.repository.OrderIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderIdempotencyService {

    private final OrderIdempotencyRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderIdempotency create(
            UUID userId,
            String idempotencyKey
    ) {
        return repository.saveAndFlush(
                OrderIdempotency.processing(
                        userId,
                        idempotencyKey
                )
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long id) {
        repository.changeStatus(
                id,
                IdempotencyStatus.PROCESSING,
                IdempotencyStatus.FAILED
        );
    }
}
