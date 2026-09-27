package com.app.order.service;

import com.app.order.client.CreatePaymentRequest;
import com.app.order.client.PaymentClient;
import com.app.order.config.OrderPaymentProperties;
import com.app.order.dto.PaymentResponse;
import com.app.order.entity.Order;
import com.app.order.exception.OrderNotFoundException;
import com.app.order.exception.OrderStateConflictException;
import com.app.order.model.OrderStatus;
import com.app.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final OrderPaymentProperties properties;

    public PaymentResponse create(
            UUID userId,
            UUID orderId,
            String clientIp
    ) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new OrderStateConflictException(
                    "Order is not pending payment"
            );
        }

        return paymentClient.create(new CreatePaymentRequest(
                order.getId(),
                order.getTotalPrice(),
                clientIp,
                paymentDeadline(order)
        ));
    }

    /**
     * Payment không được sống lâu hơn hàng đang được giữ cho nó: hạn chót là
     * hạn giữ hàng trừ đi một khoảng dự phòng. Đơn cũ chưa lưu hạn giữ hàng thì
     * trả null, payment chỉ dùng TTL của nó.
     *
     * <p>Còn ít hơn một khoảng dự phòng để trả tiền thì từ chối luôn: tránh tạo
     * payment chỉ sống vài giây, và tránh hạn chót kịp trôi qua trên đường gọi
     * sang payment-gateway (khi đó lỗi hiện ra là 502 thay vì 409).
     */
    private Instant paymentDeadline(Order order) {
        if (order.getReservationExpiresAt() == null) {
            return null;
        }
        Duration margin = properties.getDeadlineMargin();
        Instant deadline = order.getReservationExpiresAt().minus(margin);
        if (!deadline.isAfter(Instant.now().plus(margin))) {
            throw new OrderStateConflictException(
                    "Reservation expires too soon to start a payment"
            );
        }
        return deadline;
    }
}
