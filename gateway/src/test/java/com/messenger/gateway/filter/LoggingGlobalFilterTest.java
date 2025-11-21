package com.messenger.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class LoggingGlobalFilterTest {
    @Test
    void testFilterLogsApiRequest() {
        LoggingGlobalFilter filter = new LoggingGlobalFilter();
        var request = MockServerHttpRequest.get("/api/test").build();
        var exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = exchange1 -> Mono.empty();
        assertDoesNotThrow(() -> filter.filter(exchange, chain).block());
    }
}
