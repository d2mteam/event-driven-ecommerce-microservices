package com.app.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Gateway chỉ giữ public key — chỉ verify được chữ ký, không ký được token
     * giả. Private key chỉ nằm ở user-service.
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${security.app.jwt-public-key}") Resource publicKeyPem
    ) throws Exception {
        String pem = publicKeyPem.getContentAsString(StandardCharsets.UTF_8);
        byte[] der = Base64.getDecoder().decode(pem
                .replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "")
                .replaceAll("\\s", ""));
        RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));

        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();

        // Chỉ verify chữ ký + hạn là chưa đủ: refresh token (sống dài hơn nhiều)
        // cũng ký cùng khoá đó, phải chặn nó không dùng được như access token.
        OAuth2TokenValidator<Jwt> withTimestamp = JwtValidators.createDefault();
        OAuth2TokenValidator<Jwt> withTokenType =
                new JwtClaimValidator<>("token_type", "access_token"::equals);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withTimestamp, withTokenType));

        return decoder;
    }

    /**
     * /api/auth/** (login, register, refresh) là nơi CẤP token — chưa có gì để
     * verify, phải permit. Đọc sản phẩm cũng permit vì duyệt hàng không cần đăng
     * nhập. Còn lại bắt buộc access token hợp lệ.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtDecoder jwtDecoder
    ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/auth/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(jwtDecoder)))
                .build();
    }
}
