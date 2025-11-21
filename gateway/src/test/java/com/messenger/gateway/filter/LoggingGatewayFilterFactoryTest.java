package com.messenger.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class LoggingGatewayFilterFactoryTest {
    @Test
    void testApplyLogsRequestAndResponse() {
        LoggingGatewayFilterFactory factory = new LoggingGatewayFilterFactory();
        LoggingGatewayFilterFactory.Config config = new LoggingGatewayFilterFactory.Config();
        var filter = factory.apply(config);
        var request = MockServerHttpRequest.get("/").build();
        var exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = exchange1 -> Mono.empty();
        assertDoesNotThrow(() -> filter.filter(exchange, chain).block());
    }
}
