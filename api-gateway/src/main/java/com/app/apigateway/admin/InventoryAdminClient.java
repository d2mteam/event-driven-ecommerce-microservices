package com.app.apigateway.admin;

import com.app.apigateway.admin.dto.InventorySummaryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

@Component
public class InventoryAdminClient {

    private final WebClient webClient;
    private final Duration timeout;

    public InventoryAdminClient(
            WebClient.Builder builder,
            @Value("${app.services.inventory-base-url}") String baseUrl,
            @Value("${app.admin-catalog.timeout:10s}") Duration timeout
    ) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.timeout = timeout;
    }

    public Mono<List<InventorySummaryResponse>> findAll(Set<Long> productIds) {
        return webClient.post()
                .uri("/internal/inventory/batch")
                .bodyValue(productIds)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<InventorySummaryResponse>>() {
                })
                .timeout(timeout)
                .onErrorMap(
                        TimeoutException.class,
                        exception -> new ResponseStatusException(
                                HttpStatus.GATEWAY_TIMEOUT,
                                "Inventory Service timed out",
                                exception
                        )
                )
                .onErrorMap(
                        WebClientException.class,
                        exception -> new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "Cannot read Inventory Service",
                                exception
                        )
                );
    }
}
