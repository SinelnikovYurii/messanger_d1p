package com.messenger.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Value("${auth-service.url:http://auth-service:8081}")
    private String authServiceUrl;

    @Value("${user-service.url:http://core-api-service:8082}")
    private String coreApiServiceUrl;

    @Value("${websocket-server.url:ws://websocket-server:8092}")
    private String wsServiceUrl;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Authorization Service — контроллер слушает /auth/**, путь не переписываем
                .route("auth-service-java", r -> r.path("/auth/**")
                        .uri(authServiceUrl))

                // Публичный доступ к аватаркам
                .route("avatars-public-java", r -> r.path("/avatars/**", "/uploads/avatars/**")
                        .filters(f -> f.addRequestHeader("X-Public-Access", "true"))
                        .uri(coreApiServiceUrl))

                // Core API Service
                .route("core-api-service-java", r -> r.path("/api/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway-Request", "true")
                                .preserveHostHeader())
                        .uri(coreApiServiceUrl))

                // Users PreKey Bundle (Core API)
                .route("users-prekey-bundle-java", r -> r.path("/users/*/prekey-bundle")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway-Request", "true")
                                .rewritePath("/users/(?<id>[^/]+)/prekey-bundle", "/api/users/${id}/prekey-bundle"))
                        .uri(coreApiServiceUrl))

                // WebSocket Server
                .route("websocket-service-java", r -> r.path("/ws/**")
                        .filters(f -> f.rewritePath("/ws/(?<path>.*)", "/${path}"))
                        .uri(wsServiceUrl))

                .build();
    }
}
