package com.app.order.service;

import com.app.order.client.InventoryReservationItemRequest;
import com.app.order.dto.CreateOrderItemRequest;
import com.app.order.exception.InvalidOrderRequestException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class OrderItemNormalizer {

    public List<InventoryReservationItemRequest> normalize(
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
