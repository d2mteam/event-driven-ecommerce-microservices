package com.app.order.service;

import com.app.order.client.InventoryClient;
import com.app.order.client.InventoryReservationItemRequest;
import com.app.order.client.InventoryReservationRequest;
import com.app.order.client.InventoryReservationResponse;
import com.app.order.client.ProductClient;
import com.app.order.client.ProductClientResponse;
import com.app.order.client.ReservationStatus;
import com.app.order.dto.CreateOrderItemRequest;
import com.app.order.dto.CreateOrderRequest;
import com.app.order.entity.Order;
import com.app.order.exception.DownstreamServiceException;
import com.app.order.exception.InvalidOrderRequestException;
import com.app.order.exception.ProductNotFoundException;
import com.app.order.mapper.OrderMapper;
import com.app.order.model.OrderItem;
import com.app.order.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final OrderMapper orderMapper;

    public Order createPendingOrder(
            UUID userId,
            CreateOrderRequest request
    ) {
        List<InventoryReservationItemRequest> requestedItems =
                normalizeItems(request.items());
        List<ProductClientResponse> products = productClient.findProducts(
                requestedItems.stream()
                        .map(InventoryReservationItemRequest::productId)
                        .toList()
        );
        Map<Long, ProductClientResponse> productsById =
                indexAndValidateProducts(products, requestedItems);
        List<OrderItem> orderItems = createOrderItems(
                requestedItems,
                productsById
        );

        UUID orderId = UUID.randomUUID();
        InventoryReservationResponse reservation = inventoryClient.reserve(
                new InventoryReservationRequest(orderId, requestedItems)
        );
        validateReservation(orderId, reservation);

        return Order.builder()
                .id(orderId)
                .userId(userId)
                .reservationId(reservation.reservationId())
                .status(OrderStatus.PENDING_PAYMENT)
                .totalPrice(calculateTotal(orderItems))
                .items(orderItems)
                .createdAt(Instant.now())
                .build();
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

    private Map<Long, ProductClientResponse> indexAndValidateProducts(
            List<ProductClientResponse> products,
            List<InventoryReservationItemRequest> requestedItems
    ) {
        if (products.stream().anyMatch(this::isInvalidProduct)) {
            throw new DownstreamServiceException(
                    "Product Service returned invalid product data"
            );
        }

        Map<Long, ProductClientResponse> productsById = products.stream()
                .collect(Collectors.toMap(
                        ProductClientResponse::id,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        Set<Long> missingIds = requestedItems.stream()
                .map(InventoryReservationItemRequest::productId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        missingIds.removeAll(productsById.keySet());
        if (!missingIds.isEmpty()) {
            throw new ProductNotFoundException(missingIds);
        }
        return productsById;
    }

    private boolean isInvalidProduct(ProductClientResponse product) {
        return product == null
                || product.id() == null
                || product.name() == null
                || product.name().isBlank()
                || product.price() == null
                || product.price().signum() < 0;
    }

    private List<OrderItem> createOrderItems(
            List<InventoryReservationItemRequest> requestedItems,
            Map<Long, ProductClientResponse> productsById
    ) {
        List<OrderItem> orderItems = new ArrayList<>(requestedItems.size());
        for (InventoryReservationItemRequest requestedItem : requestedItems) {
            ProductClientResponse product = productsById.get(
                    requestedItem.productId()
            );
            orderItems.add(orderMapper.toOrderItem(
                    product,
                    requestedItem.quantity()
            ));
        }
        return List.copyOf(orderItems);
    }

    private void validateReservation(
            UUID orderId,
            InventoryReservationResponse reservation
    ) {
        if (reservation.reservationId() == null
                || !orderId.equals(reservation.orderId())
                || reservation.status() != ReservationStatus.HELD
                || reservation.expiresAt() == null) {
            throw new DownstreamServiceException(
                    "Inventory Service returned an invalid reservation"
            );
        }
    }

    private BigDecimal calculateTotal(List<OrderItem> items) {
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(
                        BigDecimal.valueOf(item.getQuantity())
                ))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
