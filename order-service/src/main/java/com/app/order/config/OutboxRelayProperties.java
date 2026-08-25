package com.app.order.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxRelayProperties {

    @Min(1)
    private int batchSize = 500;

    @NotNull
    private Duration sendTimeout = Duration.ofSeconds(2);

    @NotNull
    private Duration leaseDuration = Duration.ofMinutes(5);

    /** Một mức chờ chung cho mọi lần thử lại -- không tính riêng từng message. */
    @NotNull
    private Duration retryDelay = Duration.ofSeconds(5);

    /** Thử đủ số lần này mà vẫn hỏng thì chuyển FAILED và thôi. */
    @Min(1)
    private int maxAttempts = 10;

    /**
     * Cả lô bắn cùng lúc nên thời gian giữ lease không phụ thuộc batch-size:
     * xấu nhất là chờ hết một send-timeout cho toàn bộ lô.
     */
    @AssertTrue(message = "lease-duration must exceed send-timeout")
    public boolean isLeaseLongEnough() {
        if (leaseDuration == null || sendTimeout == null) {
            return true;
        }
        return leaseDuration.compareTo(sendTimeout) > 0;
    }
}
