package com.app.order.service;

import com.app.order.dto.OrderResponse;
import com.app.order.dto.PageResponse;
import com.app.order.entity.Order;
import com.app.order.mapper.OrderMapper;
import com.app.order.model.OrderStatus;
import com.app.order.repository.OrderRepository;
import com.app.order.exception.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public OrderResponse findByIdAndUserId(UUID orderId, UUID userId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .map(orderMapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> findAllByUserId(
            UUID userId,
            int page,
            int size
    ) {
        return PageResponse.from(
                orderRepository
                        .findAllByUserIdOrderByCreatedAtDesc(
                                userId,
                                PageRequest.of(page, size)
                        )
                        .map(orderMapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> findAdminOrders(
            UUID userId,
            OrderStatus status,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<Order> orders;
        if (userId != null && status != null) {
            orders = orderRepository.findAllByUserIdAndStatus(
                    userId,
                    status,
                    pageable
            );
        } else if (userId != null) {
            orders = orderRepository.findAllByUserId(userId, pageable);
        } else if (status != null) {
            orders = orderRepository.findAllByStatus(status, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }

        return PageResponse.from(orders.map(orderMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public OrderResponse findAdminOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(orderMapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
