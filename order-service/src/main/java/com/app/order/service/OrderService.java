package com.app.order.service;

import com.app.order.client.InventoryClient;
import com.app.order.client.InventoryReservationItemRequest;
import com.app.order.client.InventoryReservationRequest;
import com.app.order.client.InventoryReservationResponse;
import com.app.order.client.ProductClient;
import com.app.order.client.ProductClientResponse;
import com.app.order.client.ReservationStatus;
import com.app.order.entity.Order;
import com.app.order.event.EventVersions;
import com.app.order.event.OrderCreatedEvent;
import com.app.order.event.OrderEventType;
import com.app.order.exception.DownstreamServiceException;
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
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final OrderPersistenceService persistenceService;
    private final OrderMapper orderMapper;

    public Order create(
            UUID userId,
            UUID orderId,
            Long idempotencyId,
            List<InventoryReservationItemRequest> requestedItems
    ) {
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

        InventoryReservationResponse reservation = inventoryClient.reserve(
                new InventoryReservationRequest(orderId, requestedItems)
        );
        validateReservation(orderId, reservation);

        Instant createdAt = Instant.now();
        BigDecimal totalPrice = calculateTotal(orderItems);
        Order order = Order.builder()
                .id(orderId)
                .userId(userId)
                .reservationId(reservation.reservationId())
                .status(OrderStatus.CONFIRMED)
                .totalPrice(totalPrice)
                .items(orderItems)
                .createdAt(createdAt)
                .build();

        OrderCreatedEvent orderCreated = OrderCreatedEvent.builder()
                .messageId(UUID.randomUUID())
                .eventVersion(EventVersions.ORDER_CREATED)
                .eventType(OrderEventType.ORDER_CREATED)
                .orderId(orderId)
                .userId(userId)
                .reservationId(reservation.reservationId())
                .totalPrice(totalPrice)
                .items(orderMapper.toEventItems(orderItems))
                .occurredAt(createdAt)
                .build();

        return persistenceService.save(
                order,
                orderCreated,
                idempotencyId
        );
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
