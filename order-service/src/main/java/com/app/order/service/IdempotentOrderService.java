package com.app.order.service;

import com.app.order.dto.CreateOrderRequest;
import com.app.order.dto.OrderResponse;
import com.app.order.entity.Order;
import com.app.order.entity.OrderIdempotency;
import com.app.order.exception.IdempotencyConflictException;
import com.app.order.exception.InvalidOrderRequestException;
import com.app.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotentOrderService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final OrderService orderService;
    private final IdempotencyClaimService claimService;
    private final OrderPersistenceService persistenceService;
    private final OrderMapper orderMapper;

    public OrderResponse create(
            UUID userId,
            String idempotencyKey,
            CreateOrderRequest request
    ) {
        validateIdempotencyKey(idempotencyKey);

        OrderIdempotency claim;
        try {
            claim = claimService.claim(userId, idempotencyKey);
        } catch (DataIntegrityViolationException exception) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key was already used"
            );
        }

        try {
            Order order = orderService.createPendingOrder(userId, request);
            Order savedOrder = persistenceService.saveOrderAndCompleteClaim(
                    order,
                    claim.getId()
            );
            return orderMapper.toResponse(savedOrder);
        } catch (RuntimeException exception) {
            claimService.markFailed(claim.getId());
            throw exception;
        }
    }

    private void validateIdempotencyKey(String key) {
        if (key.isBlank() || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new InvalidOrderRequestException(
                    "Idempotency-Key must contain 1 to "
                            + MAX_IDEMPOTENCY_KEY_LENGTH
                            + " characters"
            );
        }
    }
}
