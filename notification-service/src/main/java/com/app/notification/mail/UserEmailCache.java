package com.app.notification.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Cache trong bộ nhớ tiến trình, không TTL, không tự xoá. Bản mỏng cho demo.
 *
 * <p>Lưu ý (chưa xử lý): user đổi email thì cache vẫn giữ giá trị cũ đến khi
 * service restart — muốn đúng thì phải nghe UserEmailChangedEvent hoặc đặt TTL.
 */
@Component
@RequiredArgsConstructor
public class UserEmailCache {

    private final Map<UUID, String> emailsByUserId = new ConcurrentHashMap<>();
    private final UserEmailClient userEmailClient;

    public Map<UUID, String> resolve(Set<UUID> userIds) {
        Set<UUID> missing = userIds.stream()
                .filter(userId -> !emailsByUserId.containsKey(userId))
                .collect(Collectors.toSet());

        if (!missing.isEmpty()) {
            userEmailClient.findAll(missing)
                    .forEach(user -> emailsByUserId.put(user.id(), user.email()));
        }

        return userIds.stream()
                .filter(emailsByUserId::containsKey)
                .collect(Collectors.toMap(userId -> userId, emailsByUserId::get));
    }
}
