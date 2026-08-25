package com.app.notification.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Cửa sổ gom email: mỗi lô tối đa {@code batchSize} record, chờ gom tối đa
 * {@code window}. Cũng chính {@code window} đó là hạn chót đẩy xong cả lô.
 *
 * <p>Trần 50 khớp giới hạn của /internal/users/batch bên user-service — lô to
 * hơn thì lời gọi tra email bị từ chối.
 */
@Validated
@ConfigurationProperties(prefix = "app.notification.email")
public record EmailBatchProperties(
        @Min(1) @Max(50) int batchSize,
        @NotNull @DurationMin(millis = 1) Duration window
) {
}
