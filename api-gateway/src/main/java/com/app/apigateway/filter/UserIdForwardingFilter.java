package com.app.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Sau khi JWT được verify, thay X-User-Id/X-User-Roles bằng claim đọc từ token —
 * không tin giá trị client tự gửi nữa. Route công khai (auth, đọc sản phẩm)
 * không có JWT thì đi qua nguyên trạng, không có gì để thay.
 */
@Component
public class UserIdForwardingFilter implements GlobalFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> auth.getToken())
                .map(jwt -> exchange.mutate()
                        .request(request -> request.headers(headers -> {
                            headers.set(USER_ID_HEADER, jwt.getClaimAsString("user_id"));
                            // token_type=access_token luôn có roles (JwtService.generateAccessToken) —
                            // refresh token thì bị chặn ở lớp validator trước khi tới được đây.
                            headers.set(USER_ROLES_HEADER, String.join(",", jwt.getClaimAsStringList("roles")));
                        }))
                        .build())
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }
}
