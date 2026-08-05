package com.app.order.service;

import com.app.order.client.InventoryReservationItemRequest;
import com.app.order.dto.CreateOrderRequest;
import com.app.order.dto.OrderResponse;
import com.app.order.entity.Order;
import com.app.order.entity.OrderIdempotency;
import com.app.order.exception.IdempotencyConflictException;
import com.app.order.mapper.OrderMapper;
import com.app.order.model.IdempotencyStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotentOrderService {

    private final OrderService orderService;
    private final OrderItemNormalizer itemNormalizer;
    private final OrderRequestHashService requestHashService;
    private final IdempotencyClaimService claimService;
    private final OrderPersistenceService orderPersistenceService;
    private final OrderMapper orderMapper;

    public OrderResponse create(
            UUID userId,
            String idempotencyKey,
            CreateOrderRequest request
    ) {
        List<InventoryReservationItemRequest> requestedItems =
                itemNormalizer.normalize(request.items());
        String requestHash = requestHashService.hash(requestedItems);

        OrderIdempotency claim;
        try {
            claim = claimService.createProcessingClaim(
                    userId,
                    idempotencyKey,
                    requestHash
            );
        } catch (DataIntegrityViolationException exception) {
            return handleExistingClaim(
                    userId,
                    idempotencyKey,
                    requestHash
            );
        }

        try {
            Order order = orderService.createPendingOrder(
                    userId,
                    requestedItems
            );
            Order savedOrder = orderPersistenceService.save(order);
            claimService.complete(claim.getId(), savedOrder.getId());
            return orderMapper.toResponse(savedOrder);
        } catch (RuntimeException exception) {
            claimService.markFailed(claim.getId());
            throw exception;
        }
    }


    private OrderResponse handleExistingClaim(
            UUID userId,
            String idempotencyKey,
            String requestHash
    ) {
        OrderIdempotency existing = claimService.find(userId, idempotencyKey)
                .orElseThrow(() -> new IdempotencyConflictException(
                        "Idempotency-Key was already used"
                ));

        if (!requestHash.equals(existing.getRequestHash())) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key was already used with a different request"
            );
        }
        if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
            throw new IdempotencyConflictException(
                    "The same order request is still processing"
            );
        }
        if (existing.getStatus() == IdempotencyStatus.FAILED) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key was already used by a failed request"
            );
        }

        UUID orderId = existing.getOrderId();
        if (orderId == null) {
            throw new IllegalStateException(
                    "Completed idempotency record does not have an order id"
            );
        }

        Order order = orderPersistenceService.findById(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Completed idempotency record references a missing order"
                ));
        return orderMapper.toResponse(order);
    }
}
