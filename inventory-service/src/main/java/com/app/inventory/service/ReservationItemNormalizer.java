package com.app.inventory.service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import com.app.inventory.dto.ReservationItemRequest;
import com.app.inventory.entity.ReservationItem;

/**
 * Gộp các item trùng productId và sắp xếp theo productId tăng dần.
 *
 * <p>Thứ tự sắp xếp không phải để cho đẹp: hai đơn cùng đặt nhiều sản phẩm sẽ
 * khoá theo cùng một thứ tự, nên không tạo được chu trình chờ. Vì vậy bước này
 * phải nằm trong biên của service, không đẩy ra cho caller tự làm.
 *
 * <p>TODO: tổng quantity vượt {@code Integer.MAX_VALUE} khiến
 * {@code Math.addExact} ném {@code ArithmeticException} và lan lên thành 500.
 * Order-service bắt và đổi thành lỗi 4xx; inventory chưa có kiểu exception
 * tương ứng.
 */
@Component
public class ReservationItemNormalizer {

    public List<ReservationItem> normalize(
            List<ReservationItemRequest> requestedItems
    ) {
        Map<Long, Integer> quantityByProductId = new TreeMap<>();
        for (ReservationItemRequest item : requestedItems) {
            quantityByProductId.merge(
                    item.productId(),
                    item.quantity(),
                    Math::addExact
            );
        }

        return quantityByProductId.entrySet().stream()
                .map(entry -> new ReservationItem(entry.getKey(), entry.getValue()))
                .toList();
    }
}
