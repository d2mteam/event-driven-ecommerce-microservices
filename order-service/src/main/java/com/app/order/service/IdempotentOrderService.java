package com.app.order.service;

import com.app.order.client.InventoryReservationItemRequest;
import com.app.order.dto.CreateOrderItemRequest;
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

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotentOrderService {

    private static final int MAX_KEY_LENGTH = 128;

    private final OrderService orderService;
    private final OrderIdempotencyService idempotencyService;
    private final OrderMapper orderMapper;

    public OrderResponse create(
            UUID userId,
            String idempotencyKey,
            CreateOrderRequest request
    ) {
        validateIdempotencyKey(idempotencyKey);
        List<InventoryReservationItemRequest> requestedItems =
                normalizeItems(request.items());
        OrderIdempotency idempotency;

        try {
            idempotency = idempotencyService.create(
                    userId,
                    idempotencyKey
            );
        } catch (DataIntegrityViolationException exception) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key was already used"
            );
        }

        try {
            Order order = orderService.create(
                    userId,
                    UUID.randomUUID(),
                    idempotency.getId(),
                    requestedItems
            );
            return orderMapper.toResponse(order);
        } catch (RuntimeException exception) {
            idempotencyService.fail(idempotency.getId());
            throw exception;
        }
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
