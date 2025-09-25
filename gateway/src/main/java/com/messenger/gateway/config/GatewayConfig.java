package com.messenger.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Маршрут для WebSocket - исправлен синтаксис фильтров
                .route("websocket-service", r -> r.path("/ws/**")
                        .and()
                        .header("Upgrade", "websocket")
                        .uri("ws://localhost:8092"))

                // Маршруты для авторизации
                .route("auth-service", r -> r.path("/auth/**")
                        .uri("http://localhost:8081"))

                // Маршруты для чатов (если есть отдельный сервис)
                .route("chat-service", r -> r.path("/api/chats/**")
                        .uri("http://localhost:8083"))

                // Маршруты для сообщений (если есть отдельный сервис)
                .route("message-service", r -> r.path("/api/messages/**")
                        .uri("http://localhost:8084"))

                // Маршруты для пользователей (если есть отдельный сервис)
                .route("user-service", r -> r.path("/api/users/**")
                        .uri("http://localhost:8085"))

                .build();
    }
}
