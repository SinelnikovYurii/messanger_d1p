package com.messenger.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Логируем только API запросы
        if (path.startsWith("/api/")) {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            log.info("=== Gateway Request Debug ===");
            log.info("Path: {}", path);
            log.info("Method: {}", exchange.getRequest().getMethod());
            log.info("Authorization header present: {}", authHeader != null);
            if (authHeader != null) {
                log.info("Auth header starts with Bearer: {}", authHeader.startsWith("Bearer "));
            }
            log.info("All headers: {}", exchange.getRequest().getHeaders().keySet());
            log.info("============================");
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1; // Выполняется рано в цепочке фильтров
    }
}
