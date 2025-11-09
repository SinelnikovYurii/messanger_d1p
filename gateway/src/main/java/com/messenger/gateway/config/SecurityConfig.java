package com.messenger.gateway.config;

import com.messenger.gateway.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable()) // Отключаем CORS в SecurityConfig, используем globalcors в application.yml
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/auth/**", "/public/**", "/actuator/**").permitAll()
                        .pathMatchers("/ws/**").permitAll() // WebSocket соединения
                        // ИСПРАВЛЕНИЕ: Публичный доступ к аватаркам без JWT
                        .pathMatchers("/avatars/**").permitAll()
                        .pathMatchers("/uploads/avatars/**").permitAll()
                        // Разрешаем OPTIONS запросы (CORS preflight) без аутентификации
                        .pathMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // API запросы требуют аутентификации
                        .anyExchange().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
