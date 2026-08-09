package com.app.order.service;

import com.app.order.dto.OrderResponse;
import com.app.order.dto.PageResponse;
import com.app.order.entity.Order;
import com.app.order.mapper.OrderMapper;
import com.app.order.model.OrderStatus;
import com.app.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderQueryService orderQueryService;

    @Test
    void findsAdminOrdersByUserAndStatus() {
        UUID userId = UUID.randomUUID();
        Order order = Order.builder().id(UUID.randomUUID()).build();
        OrderResponse response = org.mockito.Mockito.mock(OrderResponse.class);
        PageRequest pageable = PageRequest.of(
                1,
                10,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
        when(orderRepository.findAllByUserIdAndStatus(
                userId,
                OrderStatus.CONFIRMED,
                pageable
        )).thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(orderMapper.toResponse(order)).thenReturn(response);

        PageResponse<OrderResponse> result = orderQueryService.findAdminOrders(
                userId,
                OrderStatus.CONFIRMED,
                1,
                10
        );

        assertThat(result.content()).containsExactly(response);
        verify(orderRepository).findAllByUserIdAndStatus(
                userId,
                OrderStatus.CONFIRMED,
                pageable
        );
    }

    @Test
    void letsAdminReadAnOrderWithoutUserOwnershipFilter() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).build();
        OrderResponse response = org.mockito.Mockito.mock(OrderResponse.class);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(response);

        assertThat(orderQueryService.findAdminOrder(orderId)).isSameAs(response);

        verify(orderRepository).findById(orderId);
    }
}
