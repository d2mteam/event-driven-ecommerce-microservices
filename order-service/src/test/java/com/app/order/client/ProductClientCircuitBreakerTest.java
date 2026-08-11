package com.app.order.client;

import com.app.order.exception.DownstreamServiceException;
import com.app.order.exception.ProductNotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(
        classes = ProductClientCircuitBreakerTest.TestApplication.class,
        properties = {
                "resilience4j.circuitbreaker.instances.product-service.sliding-window-size=2",
                "resilience4j.circuitbreaker.instances.product-service.minimum-number-of-calls=2",
                "resilience4j.circuitbreaker.instances.product-service.failure-rate-threshold=100",
                "resilience4j.circuitbreaker.instances.product-service.wait-duration-in-open-state=1m"
        }
)
class ProductClientCircuitBreakerTest {

    @Autowired
    private ProductClient productClient;

    @Autowired
    private MockRestServiceServer server;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("product-service").reset();
    }

    @Test
    void opensAfterRepeatedDownstreamFailures() {
        server.expect(twice(), requestTo("http://product-service/api/products/batch"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> productClient.findProducts(List.of(1L)))
                .isInstanceOf(DownstreamServiceException.class);
        assertThatThrownBy(() -> productClient.findProducts(List.of(1L)))
                .isInstanceOf(DownstreamServiceException.class);

        assertThatThrownBy(() -> productClient.findProducts(List.of(1L)))
                .isInstanceOf(CallNotPermittedException.class);
        server.verify();
    }

    @Test
    void doesNotOpenForProductNotFound() {
        server.expect(twice(), requestTo("http://product-service/api/products/batch"))
                .andRespond(withResourceNotFound());
        server.expect(requestTo("http://product-service/api/products/batch"))
                .andRespond(withSuccess(
                        "[{\"id\":1,\"name\":\"Keyboard\",\"price\":100000}]",
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> productClient.findProducts(List.of(1L)))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> productClient.findProducts(List.of(1L)))
                .isInstanceOf(ProductNotFoundException.class);

        productClient.findProducts(List.of(1L));
        server.verify();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        MockRestServiceServer mockRestServiceServer(RestClient.Builder builder) {
            return MockRestServiceServer.bindTo(builder).build();
        }

        @Bean
        ProductClient productClient(
                RestClient.Builder builder,
                MockRestServiceServer server
        ) {
            return new ProductClient(builder.baseUrl("http://product-service").build());
        }
    }
}
