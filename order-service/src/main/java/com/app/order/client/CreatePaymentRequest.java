package com.app.order.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * deadline: payment không được sống quá mốc này, để luôn hết hạn trước khi
 * inventory nhả hàng. null: không cắt theo hạn giữ hàng, payment chỉ dùng TTL
 * của nó (đơn cũ chưa lưu hạn giữ hàng).
 */
public record CreatePaymentRequest(
        UUID orderId,
        BigDecimal amount,
        String clientIp,
        Instant deadline
) {
}
