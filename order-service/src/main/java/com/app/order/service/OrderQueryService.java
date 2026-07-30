package com.app.order.service;

import com.app.order.dto.OrderResponse;
import com.app.order.dto.PageResponse;
import com.app.order.mapper.OrderMapper;
import com.app.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

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
}
