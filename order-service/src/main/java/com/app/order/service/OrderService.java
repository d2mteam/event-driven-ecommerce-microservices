package com.app.order.service;

import com.app.order.client.InventoryClient;
import com.app.order.client.InventoryReservationItemRequest;
import com.app.order.client.InventoryReservationRequest;
import com.app.order.client.InventoryReservationResponse;
import com.app.order.client.ProductClient;
import com.app.order.client.ProductClientResponse;
import com.app.order.entity.Order;
import com.app.order.mapper.OrderMapper;
import com.app.order.model.OrderItem;
import com.app.order.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            List<InventoryReservationItemRequest> requestedItems
    ) {
        List<ProductClientResponse> products = productClient.findProducts(
                requestedItems.stream()
                        .map(InventoryReservationItemRequest::productId)
                        .toList()
        );
        Map<Long, ProductClientResponse> productsById = products.stream()
                .collect(Collectors.toMap(
                        ProductClientResponse::id,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        List<OrderItem> orderItems = createOrderItems(
                requestedItems,
                productsById
        );

        UUID orderId = UUID.randomUUID();
        InventoryReservationResponse reservation = inventoryClient.reserve(
                new InventoryReservationRequest(orderId, requestedItems)
        );

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


    private BigDecimal calculateTotal(List<OrderItem> items) {
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(
                        BigDecimal.valueOf(item.getQuantity())
                ))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
