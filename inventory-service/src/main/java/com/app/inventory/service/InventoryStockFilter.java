package com.app.inventory.service;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.app.inventory.config.InventoryStockFilterProperties;
import com.app.inventory.entity.ReservationItem;
import com.app.inventory.exception.InventoryConflictException;

import lombok.RequiredArgsConstructor;

/**
 * Bộ lọc tồn kho đặt trước luồng DB, giảm tải khoá khi có hot key.
 *
 * <p>Redis chỉ trả lời "chắc chắn không đủ" hoặc "chưa biết". Luồng DB phía sau
 * vẫn khoá và kiểm tra đầy đủ, nên không trạng thái nào của Redis gây oversell.
 * Mặc định tắt.
 *
 * <p>TODO — edge case chưa xử lý, cần làm nếu chạy thật:
 * <ul>
 *   <li>Redis chết hoặc timeout: exception lan lên và chặn đơn hàng.
 *       Cần try/catch fail-open để rơi xuống DB thay vì từ chối.</li>
 *   <li>Gọi ngoài transaction: {@code registerSynchronization} ném
 *       {@code IllegalStateException}. Cần kiểm tra
 *       {@code isSynchronizationActive()} trước.</li>
 *   <li>Giá trị cache âm: bộ lọc sẽ chặn sạch sản phẩm đó tới hết TTL.</li>
 *   <li>Đơn nhiều sản phẩm: mỗi item một lệnh GET. Dùng MGET nếu cần.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryStockFilter {

    private final StringRedisTemplate redis;
    private final InventoryStockFilterProperties properties;

    /**
     * Từ chối sớm nếu cache nói không đủ hàng.
     *
     * <p>Phải gọi SAU bước kiểm tra idempotency theo orderId — nếu không, retry
     * của một đơn đã giữ hàng thành công sẽ bị bộ lọc từ chối.
     */
    public void rejectIfKnownInsufficient(List<ReservationItem> items) {
        if (!properties.isEnabled()) {
            return;
        }

        for (ReservationItem item : items) {
            String cached = redis.opsForValue().get(key(item.productId()));
            if (cached == null) {
                continue;   // chưa biết -> để DB quyết
            }

            int available = Integer.parseInt(cached);
            if (available < item.quantity()) {
                throw InventoryConflictException.insufficientStock(
                        item.productId(),
                        item.quantity(),
                        available
                );
            }
        }
    }

    /**
     * Ghi lượng còn bán được vào cache, sau khi transaction commit.
     *
     * <p>Ghi trong transaction rồi rollback sẽ để lại giá trị thấp hơn thực tế,
     * khiến bộ lọc từ chối oan những đơn lẽ ra bán được.
     */
    public void refreshAfterCommit(Map<Long, Integer> availableByProductId) {
        if (!properties.isEnabled()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            availableByProductId.forEach((productId, available) ->
                                    redis.opsForValue().set(
                                            key(productId),
                                            String.valueOf(available),
                                            properties.getTtl()
                                    ));
                        } catch (RuntimeException exception) {
                            log.warn("Cannot refresh stock filter cache", exception);
                        }
                    }
                });
    }

    private String key(Long productId) {
        return properties.getKeyPrefix() + productId;
    }
}
