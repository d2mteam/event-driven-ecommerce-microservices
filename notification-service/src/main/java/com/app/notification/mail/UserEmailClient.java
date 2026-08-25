package com.app.notification.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Tra email từ User Service. Lỗi tạm thời sẽ được Kafka listener retry. */
@Component
public class UserEmailClient {

    private final RestClient restClient;

    /**
     * Timeout là bắt buộc chứ không phải cho chắc: User Service treo (chứ không
     * phải trả lỗi) thì lời gọi này không bao giờ kết thúc, và listener email
     * đứng luôn -- retry của Kafka cũng vô dụng vì lần thử đầu chưa xong.
     */
    UserEmailClient(
            @Value("${app.user-service.base-url}") String baseUrl,
            @Value("${app.user-service.connect-timeout}") Duration connectTimeout,
            @Value("${app.user-service.read-timeout}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Một lời gọi cho cả lô thay vì mỗi email một lời gọi. User không tìm thấy
     * thì vắng mặt trong map.
     */
    public Map<UUID, String> findEmails(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserEmailResponse> users = restClient.post()
                .uri("/internal/users/batch")
                .body(userIds)
                .retrieve()
                .body(new ParameterizedTypeReference<List<UserEmailResponse>>() {
                });
        if (users == null) {
            return Map.of();
        }
        return users.stream().collect(Collectors.toMap(
                UserEmailResponse::id,
                UserEmailResponse::email
        ));
    }
}
