package com.app.order.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.payment")
public class OrderPaymentProperties {

    /**
     * Payment phải hết hạn sớm hơn hạn giữ hàng ít nhất khoảng này, để một
     * payment thành công sát giờ vẫn kịp đi hai chặng outbox + Kafka
     * (payment → order, rồi ORDER_CONFIRMED → inventory chốt trừ hàng) trước
     * khi sweeper của inventory nhả hàng. Cũng là thời gian trả tiền tối
     * thiểu: còn ít hơn thế thì không cho tạo payment.
     */
    @NotNull
    private Duration deadlineMargin = Duration.ofMinutes(2);
}
