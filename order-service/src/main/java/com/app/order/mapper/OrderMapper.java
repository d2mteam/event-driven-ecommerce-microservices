package com.app.order.mapper;

import com.app.order.client.ProductClientResponse;
import com.app.order.dto.OrderItemResponse;
import com.app.order.dto.OrderResponse;
import com.app.order.entity.Order;
import com.app.order.event.OrderCreatedItem;
import com.app.order.model.OrderItem;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderMapper {

    OrderResponse toResponse(Order order);

    OrderItemResponse toItemResponse(OrderItem item);

    List<OrderCreatedItem> toEventItems(List<OrderItem> items);

    OrderCreatedItem toEventItem(OrderItem item);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "unitPrice", source = "product.price")
    @Mapping(target = "quantity", source = "quantity")
    OrderItem toOrderItem(ProductClientResponse product, Integer quantity);
}
