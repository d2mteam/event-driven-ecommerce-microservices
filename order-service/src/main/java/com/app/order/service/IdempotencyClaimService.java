package com.app.order.service;

import com.app.order.entity.OrderIdempotency;
import com.app.order.model.IdempotencyStatus;
import com.app.order.repository.OrderIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyClaimService {

    private final OrderIdempotencyRepository repository;

    @Transactional
    public OrderIdempotency createProcessingClaim(
            UUID userId,
            String idempotencyKey,
            String requestHash
    ) {
        return repository.save(
                OrderIdempotency.processing(userId, idempotencyKey, requestHash)
        );
    }

    @Transactional(readOnly = true)
    public Optional<OrderIdempotency> find(
            UUID userId,
            String idempotencyKey
    ) {
        return repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

    @Transactional
    public void markFailed(Long claimId) {
        repository.changeStatus(
                claimId,
                IdempotencyStatus.PROCESSING,
                IdempotencyStatus.FAILED
        );
    }

    @Transactional
    public void complete(Long claimId, UUID orderId) {
        int updated = repository.complete(
                claimId,
                IdempotencyStatus.PROCESSING,
                IdempotencyStatus.COMPLETED,
                orderId
        );
        if (updated != 1) {
            throw new IllegalStateException(
                    "Idempotency record is not processing"
            );
        }
    }
}
