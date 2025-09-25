package com.messenger.gateway.security;


import com.messenger.gateway.service.AuthServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;


@Slf4j
@Component
public class JwtAuthenticationFilter implements WebFilter {

    private final AuthServiceClient authServiceClient;


    public JwtAuthenticationFilter(AuthServiceClient authServiceClient) {
        this.authServiceClient = authServiceClient;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);

        return authServiceClient.validateAndGetUserId(token)
                .flatMap(userId -> {
                    if (userId != null) {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(userId, null, null);
                        log.info("Authenticated user with ID: {}", userId);
                        return chain.filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authToken));
                    } else {
                        log.warn("JWT token validation failed. Token: {}", token);
                        return chain.filter(exchange);
                    }
                }).onErrorResume(e -> {
                    log.error("Error during JWT validation", e);
                    return chain.filter(exchange);
                });
    }
}
