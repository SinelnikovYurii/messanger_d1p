package com.messenger.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class LoggingGatewayFilterFactory extends AbstractGatewayFilterFactory<LoggingGatewayFilterFactory.Config> {

    public LoggingGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            log.info("Gateway request: {} {}",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI());

            return chain.filter(exchange)
                    .then(Mono.fromRunnable(() -> {
                        log.info("Gateway response: {}",
                                exchange.getResponse().getStatusCode());
                    }));
        };
    }

    public static class Config {
        // Конфигурация фильтра (если нужна)
    }
}
