package com.app.apigateway.admin;

import com.app.apigateway.admin.dto.AdminProductResponse;
import com.app.apigateway.admin.dto.PageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
public class ProductAdminClient {

    private final WebClient webClient;
    private final Duration timeout;

    public ProductAdminClient(
            WebClient.Builder builder,
            @Value("${app.services.product-base-url}") String baseUrl,
            @Value("${app.admin-catalog.timeout:10s}") Duration timeout
    ) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.timeout = timeout;
    }

    public Mono<PageResponse<AdminProductResponse>> findProducts(
            int page,
            int size,
            String sort,
            String status,
            String query
    ) {
        return webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/internal/admin/products")
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .queryParam("sort", sort);
                    if (status != null && !status.isBlank()) {
                        uriBuilder.queryParam("status", status);
                    }
                    if (query != null && !query.isBlank()) {
                        uriBuilder.queryParam("query", query);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .bodyToMono(
                        new ParameterizedTypeReference<PageResponse<AdminProductResponse>>() {
                        }
                )
                .timeout(timeout)
                .onErrorMap(
                        TimeoutException.class,
                        exception -> new ResponseStatusException(
                                HttpStatus.GATEWAY_TIMEOUT,
                                "Product Service timed out",
                                exception
                        )
                )
                .onErrorMap(
                        WebClientException.class,
                        exception -> new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "Cannot read Product Service",
                                exception
                        )
                );
    }
}
