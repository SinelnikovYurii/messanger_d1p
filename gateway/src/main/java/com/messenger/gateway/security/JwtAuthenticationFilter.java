package com.messenger.gateway.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Component
public class JwtAuthenticationFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Пропускаем OPTIONS запросы (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod().name())) {
            logger.debug("Skipping authentication for OPTIONS request: {}", request.getPath().value());
            return chain.filter(exchange);
        }

        // Пропускаем аутентификацию для публичных эндпоинтов
        String path = request.getPath().value();
        if (isPublicPath(path)) {
            logger.debug("Skipping authentication for public path: {}", path);
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = null;

        // Проверяем токен в заголовке Authorization
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // Если токена нет в заголовке, проверяем query параметр (для GET запросов файлов/изображений)
        if (token == null) {
            token = request.getQueryParams().getFirst("token");
            if (token != null) {
                logger.debug("Found token in query parameter for path: {}", path);
            }
        }

        // Если нет токена вообще, продолжаем без аутентификации
        if (token == null) {
            logger.debug("No token found for path: {}", path);
            return chain.filter(exchange);
        }

        try {
            if (jwtService.isTokenValid(token)) {
                String username = jwtService.extractUsername(token);
                Long userId = jwtService.extractUserId(token);

                logger.debug("JWT token valid for user: {} (ID: {})", username, userId);

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                    username, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );

                // Добавляем userId в заголовки для передачи в downstream сервисы
                ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId.toString())
                    .header("X-Username", username)
                    .header("Authorization", "Bearer " + token)
                    .build();

                ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(mutatedRequest)
                    .build();

                return chain.filter(mutatedExchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
            } else {
                logger.warn("JWT token validation failed for path: {}", path);
                return unauthorizedResponse(exchange, "Invalid JWT token");
            }
        } catch (Exception e) {
            logger.error("JWT token processing error for path: {}: {}", path, e.getMessage());
            return unauthorizedResponse(exchange, "JWT token processing error: " + e.getMessage());
        }
    }

    private boolean isPublicPath(String path) {
        return path != null && (
            path.startsWith("/auth/") ||
            path.equals("/") ||
            path.startsWith("/public/") ||
            path.startsWith("/actuator/") ||
            path.startsWith("/debug/")
        );
    }

    protected Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");

        String body = String.format("{\"error\":\"Unauthorized\",\"message\":\"%s\"}", message);
        org.springframework.core.io.buffer.DataBuffer buffer = response.bufferFactory().wrap(body.getBytes());

        return response.writeWith(Mono.just(buffer));
    }
}
