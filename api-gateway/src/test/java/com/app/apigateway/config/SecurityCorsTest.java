package com.app.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityCorsTest {

    @LocalServerPort
    private int port;

    @Test
    void permitsBrowserPreflightWithoutAuthentication() {
        WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build()
                .method(HttpMethod.OPTIONS)
                .uri("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"
                );
    }

    @Test
    void permitsPublicCategoryReadsWithoutAuthentication() {
        WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build()
                .get()
                .uri("/api/categories")
                .exchange()
                .expectStatus()
                .value(status -> assertThat(status).isNotEqualTo(401));
    }
}
