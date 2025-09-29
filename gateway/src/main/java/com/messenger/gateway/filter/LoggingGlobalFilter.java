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

        // Логируем только базовую информацию для API запросов
        if (path.startsWith("/api/") && log.isDebugEnabled()) {
            String method = exchange.getRequest().getMethod().toString();
            boolean hasAuth = exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION);
            log.debug("Gateway request: {} {}, Auth: {}", method, path, hasAuth);
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1; // Выполняется рано в цепочке фильтров
    }
}
