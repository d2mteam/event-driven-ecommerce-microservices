package com.app.apigateway.admin;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ProductAdminClientTest {

    @Test
    void sendsCategoryIdToProductService() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            requestedUri.set(request.url());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            {
                              "content": [],
                              "page": 0,
                              "size": 20,
                              "totalElements": 0,
                              "totalPages": 0,
                              "last": true
                            }
                            """)
                    .build());
        });
        ProductAdminClient client = new ProductAdminClient(
                builder,
                "http://product-service",
                Duration.ofSeconds(1)
        );

        client.findProducts(0, 20, "id,asc", null, null, 7L).block();

        assertThat(requestedUri.get().getRawQuery())
                .contains("categoryId=7")
                .doesNotContain("category=");
    }
}
