package com.app.inventory.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.app.inventory.config.InventoryStockFilterProperties;
import com.app.inventory.entity.ReservationItem;
import com.app.inventory.exception.InventoryConflictException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bộ lọc tồn kho đặt <b>trước</b> luồng DB, dùng Redis làm cache-aside.
 *
 * <p><b>Redis ở đây không giữ chỗ.</b> Nó chỉ trả lời "chắc chắn không đủ" hoặc
 * "chưa biết". Luồng DB phía sau vẫn khoá bi quan và kiểm tra đầy đủ, nên không
 * trạng thái nào của Redis có thể gây bán vượt tồn kho:
 *
 * <ul>
 *   <li>Redis <b>cao hơn</b> thực tế → request lọt xuống DB → DB từ chối. Vô hại.</li>
 *   <li>Redis <b>thấp hơn</b> thực tế → từ chối oan, tối đa trong một khoảng TTL.</li>
 *   <li>Redis <b>chết</b> → bỏ qua bộ lọc, đi thẳng vào DB.</li>
 *   <li>Redis <b>thiếu key</b> → chưa biết, cho qua để DB quyết.</li>
 * </ul>
 *
 * <p>Bộ lọc chỉ có tác dụng <b>sau khi</b> hàng đã hết: lúc còn đang tranh nhau
 * mua thì mọi request đều có cơ hội thật nên đều phải xuống DB. Với flash sale,
 * nó chặn phần đuôi — thường là phần lớn nhất của cơn tải.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryStockFilter {

    private final StringRedisTemplate redisTemplate;
    private final InventoryStockFilterProperties properties;

    /**
     * Bản no-op, dùng cho test hoặc khi muốn dựng service thủ công.
     *
     * <p>An toàn khi không có Redis: cả hai method public đều kiểm tra
     * {@code enabled} ở dòng đầu nên không bao giờ chạm tới template.
     */
    public static InventoryStockFilter disabled() {
        InventoryStockFilterProperties disabledProperties =
                new InventoryStockFilterProperties();
        disabledProperties.setEnabled(false);
        return new InventoryStockFilter(null, disabledProperties);
    }

    /**
     * Từ chối sớm nếu cache nói chắc chắn không đủ hàng.
     *
     * <p>LƯU Ý THỨ TỰ: phải gọi <b>sau</b> bước kiểm tra idempotency theo
     * {@code orderId}. Nếu gọi trước, một request retry của đơn đã giữ hàng
     * thành công sẽ bị bộ lọc từ chối (vì lúc đó hàng đã hết), làm hỏng tính
     * idempotent của {@code reserve}.
     */
    public void rejectIfKnownInsufficient(List<ReservationItem> items) {
        if (!properties.isEnabled()) {
            return;
        }

        for (ReservationItem item : items) {
            Integer available = cachedAvailable(item.productId());

            // null = chưa biết (chưa cache hoặc Redis lỗi) → nhường DB quyết định.
            // Đây là điểm bắt buộc phải đúng: key vắng mặt KHÁC hoàn toàn "hết hàng".
            if (available != null && available < item.quantity()) {
                log.debug(
                        "Stock filter rejected productId={} requested={} cached={}",
                        item.productId(),
                        item.quantity(),
                        available
                );
                throw InventoryConflictException.insufficientStock(
                        item.productId(),
                        item.quantity(),
                        available
                );
            }
        }
    }

    /**
     * Ghi lại lượng còn bán được sau khi transaction commit.
     *
     * <p>Phải là <b>sau commit</b>: nếu ghi trong transaction rồi rollback,
     * cache sẽ giữ một giá trị chưa từng tồn tại — và nếu giá trị đó thấp hơn
     * thực tế thì gây từ chối oan.
     *
     * <p>Khi không có transaction đang chạy thì ghi luôn.
     */
    public void refreshAfterCommit(Map<Long, Integer> availableByProductId) {
        if (!properties.isEnabled() || availableByProductId.isEmpty()) {
            return;
        }

        Map<Long, Integer> snapshot = new LinkedHashMap<>(availableByProductId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            write(snapshot);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        write(snapshot);
                    }
                });
    }

    private Integer cachedAvailable(Long productId) {
        try {
            String value = redisTemplate.opsForValue().get(key(productId));
            return value == null ? null : Integer.valueOf(value);
        } catch (RuntimeException exception) {
            // Fail-open: Redis hỏng không được chặn đơn hàng.
            log.debug("Stock filter read failed for productId={}", productId, exception);
            return null;
        }
    }

    private void write(Map<Long, Integer> availableByProductId) {
        availableByProductId.forEach((productId, available) -> {
            try {
                redisTemplate.opsForValue().set(
                        key(productId),
                        String.valueOf(Math.max(available, 0)),
                        properties.getTtl()
                );
            } catch (RuntimeException exception) {
                // Best-effort: transaction đã commit thành công rồi, không được
                // để lỗi cache làm hỏng kết quả. Mất lần ghi này thì cache giữ
                // giá trị cũ (cao hơn thực tế) — đúng hướng an toàn.
                log.debug(
                        "Stock filter refresh failed for productId={}",
                        productId,
                        exception
                );
            }
        });
    }

    private String key(Long productId) {
        return properties.getKeyPrefix() + productId;
    }
}
