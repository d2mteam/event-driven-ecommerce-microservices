package com.app.order.service;

import com.app.order.client.CreatePaymentRequest;
import com.app.order.client.PaymentClient;
import com.app.order.dto.PaymentResponse;
import com.app.order.entity.Order;
import com.app.order.exception.OrderNotFoundException;
import com.app.order.exception.OrderStateConflictException;
import com.app.order.model.OrderStatus;
import com.app.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;

    public PaymentResponse create(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new OrderStateConflictException(
                    "Order is not pending payment"
            );
        }

        return paymentClient.create(new CreatePaymentRequest(
                order.getId(),
                order.getTotalPrice()
        ));
    }
}
