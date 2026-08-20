package com.app.notification.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Set;
import java.util.UUID;

/** Tra email từ User Service. Lỗi tạm thời sẽ được Kafka listener retry. */
@Component
public class UserEmailClient {

    private final RestClient restClient;

    UserEmailClient(@Value("${app.user-service.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    public String findEmail(UUID userId) {
        var users = restClient.post()
                .uri("/internal/users/batch")
                .body(Set.of(userId))
                .retrieve()
                .body(new ParameterizedTypeReference<java.util.List<UserEmailResponse>>() {
                });
        if (users == null) {
            return null;
        }
        return users.stream()
                .filter(user -> user.id().equals(userId))
                .map(UserEmailResponse::email)
                .findFirst()
                .orElse(null);
    }
}
