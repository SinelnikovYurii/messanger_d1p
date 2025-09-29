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
                // Маршрут для Authorization Service
                .route("auth-service", r -> r.path("/auth/**")
                        .uri("http://localhost:8081"))

                // Маршрут для Core API Service
                .route("core-api-service", r -> r.path("/api/**")
                        .uri("http://localhost:8082"))

                // Маршрут для WebSocket Server - исправлен порт
                .route("websocket-service", r -> r.path("/ws/**")
                        .uri("ws://localhost:8092"))

                .build();
    }
}
