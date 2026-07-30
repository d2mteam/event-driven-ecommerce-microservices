package com.app.order.service;

import com.app.order.client.InventoryReservationItemRequest;
import com.app.order.dto.CreateOrderItemRequest;
import com.app.order.dto.CreateOrderRequest;
import com.app.order.dto.OrderResponse;
import com.app.order.entity.Order;
import com.app.order.exception.IdempotencyConflictException;
import com.app.order.exception.InvalidOrderRequestException;
import com.app.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotentOrderService {

    private static final int MAX_KEY_LENGTH = 128;

    private final OrderService orderService;
    private final OrderPersistenceService persistenceService;
    private final OrderRequestHasher requestHasher;
    private final OrderMapper orderMapper;

    public OrderResponse create(
            UUID userId,
            String idempotencyKey,
            CreateOrderRequest request
    ) {
        validateIdempotencyKey(idempotencyKey);
        List<InventoryReservationItemRequest> requestedItems =
                normalizeItems(request.items());
        String requestHash = requestHasher.hash(requestedItems);
        Order order = persistenceService
                .findByIdempotencyKey(userId, idempotencyKey)
                .orElse(null);

        if (order == null) {
            try {
                order = orderService.create(
                        userId,
                        UUID.randomUUID(),
                        idempotencyKey,
                        requestHash,
                        requestedItems
                );
            } catch (DataIntegrityViolationException exception) {
                order = persistenceService
                        .findByIdempotencyKey(
                                userId,
                                idempotencyKey
                        )
                        .orElseThrow(() -> exception);
            }
        }

        if (!Objects.equals(order.getRequestHash(), requestHash)) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key was already used for another request"
            );
        }

        return orderMapper.toResponse(order);
    }

    private void validateIdempotencyKey(String key) {
        if (key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new InvalidOrderRequestException(
                    "Idempotency-Key must contain 1 to "
                            + MAX_KEY_LENGTH
                            + " characters"
            );
        }
    }

    private List<InventoryReservationItemRequest> normalizeItems(
            List<CreateOrderItemRequest> items
    ) {
        Map<Long, Integer> quantitiesByProduct = new TreeMap<>();
        try {
            for (CreateOrderItemRequest item : items) {
                quantitiesByProduct.merge(
                        item.productId(),
                        item.quantity(),
                        Math::addExact
                );
            }
        } catch (ArithmeticException exception) {
            throw new InvalidOrderRequestException(
                    "The total quantity of a product is too large"
            );
        }

        return quantitiesByProduct.entrySet().stream()
                .map(entry -> new InventoryReservationItemRequest(
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();
    }
}
