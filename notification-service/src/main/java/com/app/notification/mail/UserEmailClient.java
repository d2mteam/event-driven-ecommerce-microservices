package com.app.notification.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bản mỏng, không circuit breaker, không retry — demo nên chưa cần. */
@Component
public class UserEmailClient {

    private final RestClient restClient;

    UserEmailClient(@Value("${app.user-service.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    public List<UserEmailResponse> findAll(Set<UUID> userIds) {
        return restClient.post()
                .uri("/internal/users/batch")
                .body(userIds)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}
