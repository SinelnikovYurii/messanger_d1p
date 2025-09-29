package com.messenger.gateway.config;

import org.springframework.context.annotation.Configuration;

// CORS конфигурация перенесена в SecurityConfig для централизованного управления
@Configuration
public class CorsConfig {
    // Конфигурация убрана - используется SecurityConfig.corsConfigurationSource()
}
