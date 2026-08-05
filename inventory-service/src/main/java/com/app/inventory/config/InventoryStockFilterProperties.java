package com.app.inventory.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** Cấu hình bộ lọc tồn kho. Mặc định tắt: bật khi cần giảm tải hot key. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.inventory.stock-filter")
public class InventoryStockFilterProperties {

    private boolean enabled = false;

    /** Chặn trên cho mọi sai lệch: hết TTL thì cache biến mất và đọc lại từ DB. */
    private Duration ttl = Duration.ofSeconds(60);

    private String keyPrefix = "inv:avail:";
}
