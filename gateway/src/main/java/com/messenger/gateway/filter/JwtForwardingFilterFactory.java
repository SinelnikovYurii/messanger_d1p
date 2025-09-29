package com.messenger.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@Component
@Slf4j
public class JwtForwardingFilterFactory extends AbstractGatewayFilterFactory<JwtForwardingFilterFactory.Config> {

    public JwtForwardingFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerWebExchange modifiedExchange = exchange;

            // Получаем Authorization заголовок из исходного запроса
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                log.debug("Forwarding JWT token to downstream service: Bearer ***");

                // Явно добавляем Authorization заголовок в запрос к downstream сервису
                modifiedExchange = exchange.mutate()
                    .request(exchange.getRequest().mutate()
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .build())
                    .build();
            } else {
                log.debug("No JWT token found in request headers");
            }

            return chain.filter(modifiedExchange);
        };
    }

    public static class Config {
        // Конфигурационные параметры (если нужны)
    }
}
