package com.app.order.service;

import com.app.order.entity.OrderIdempotency;
import com.app.order.model.IdempotencyStatus;
import com.app.order.repository.OrderIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyClaimService {

    private final OrderIdempotencyRepository repository;

    @Transactional
    public OrderIdempotency claim(UUID userId, String idempotencyKey) {
        return repository.save(
                OrderIdempotency.processing(userId, idempotencyKey)
        );
    }

    @Transactional
    public void markFailed(Long claimId) {
        repository.changeStatus(
                claimId,
                IdempotencyStatus.PROCESSING,
                IdempotencyStatus.FAILED
        );
    }
}
