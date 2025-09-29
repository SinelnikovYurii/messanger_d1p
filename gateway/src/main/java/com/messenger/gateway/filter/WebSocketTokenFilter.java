package com.messenger.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class WebSocketTokenFilter extends AbstractGatewayFilterFactory<WebSocketTokenFilter.Config> {

    public WebSocketTokenFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // Логируем оригинальный запрос
            System.out.println("Original WebSocket request URI: " + request.getURI());
            System.out.println("Query params: " + request.getQueryParams());

            // Проверяем, есть ли токен в query параметрах
            String token = request.getQueryParams().getFirst("token");
            if (token != null) {
                System.out.println("Found token in request: " + token.substring(0, Math.min(token.length(), 20)) + "...");

                // Создаем новый URI с сохранением всех query параметров
                String newUri = UriComponentsBuilder.fromUri(request.getURI())
                    .build()
                    .toString();

                System.out.println("Forwarding to: " + newUri);
            } else {
                System.out.println("No token found in WebSocket request");
            }

            return chain.filter(exchange);
        };
    }

    public static class Config {
        // Конфигурационные параметры при необходимости
    }
}
