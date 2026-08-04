package com.app.inventory.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Cấu hình bộ lọc tồn kho đặt trước luồng DB.
 *
 * <p>Mặc định {@code enabled = false}: bộ lọc không nằm trên đường đi của tính
 * đúng đắn, bật lên chỉ để giảm tải DB khi có hot key (flash sale). Tắt đi thì
 * hệ thống chạy y như trước.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.inventory.stock-filter")
public class InventoryStockFilterProperties {

    /** Mặc định tắt. Bật bằng env INVENTORY_STOCK_FILTER_ENABLED=true. */
    private boolean enabled = false;

    /**
     * Thời gian sống của một entry cache.
     *
     * <p>Đây là chặn trên cho mọi sai lệch: dù cache có lệch vì lý do gì, sau
     * TTL nó biến mất và request kế tiếp rơi xuống DB đọc lại giá trị thật.
     */
    @NotNull
    private Duration ttl = Duration.ofSeconds(60);

    @NotBlank
    private String keyPrefix = "inv:avail:";
}
