package com.app.apigateway.admin;

import com.app.apigateway.ApiGatewayApplication;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = {
                ApiGatewayApplication.class,
                ProductAdminClientCircuitBreakerTest.ClientTestConfig.class
        },
        properties = {
                "resilience4j.circuitbreaker.instances.admin-product-service.sliding-window-size=2",
                "resilience4j.circuitbreaker.instances.admin-product-service.minimum-number-of-calls=2",
                "resilience4j.circuitbreaker.instances.admin-product-service.failure-rate-threshold=100",
                "resilience4j.circuitbreaker.instances.admin-product-service.wait-duration-in-open-state=1m"
        }
)
class ProductAdminClientCircuitBreakerTest {

    @Autowired
    private ProductAdminClient productClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private AtomicInteger requestCount;

    @BeforeEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("admin-product-service").reset();
        requestCount.set(0);
    }

    @Test
    void opensAfterRepeatedProductServiceFailures() {
        assertThatThrownBy(this::findProducts)
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(this::findProducts)
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(this::findProducts)
                .isInstanceOf(CallNotPermittedException.class);
        assertThat(requestCount).hasValue(2);
    }

    private void findProducts() {
        productClient.findProducts(0, 20, "id,asc", null, null, null)
                .block();
    }

    @TestConfiguration
    static class ClientTestConfig {

        @Bean
        AtomicInteger requestCount() {
            return new AtomicInteger();
        }

        @Bean
        @Primary
        WebClient.Builder failingWebClientBuilder(AtomicInteger requestCount) {
            return WebClient.builder().exchangeFunction(request -> {
                requestCount.incrementAndGet();
                return Mono.just(ClientResponse.create(
                        HttpStatus.INTERNAL_SERVER_ERROR
                ).build());
            });
        }
    }
}
