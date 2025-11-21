package com.messenger.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class WebSocketTokenFilterTest {
    @Test
    void testApplyWithToken() {
        WebSocketTokenFilter filter = new WebSocketTokenFilter();
        WebSocketTokenFilter.Config config = new WebSocketTokenFilter.Config();
        var request = MockServerHttpRequest.get("/").queryParam("token", "sometoken").build();
        var exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = exchange1 -> Mono.empty();
        assertDoesNotThrow(() -> filter.apply(config).filter(exchange, chain).block());
    }

    @Test
    void testApplyWithoutToken() {
        WebSocketTokenFilter filter = new WebSocketTokenFilter();
        WebSocketTokenFilter.Config config = new WebSocketTokenFilter.Config();
        var request = MockServerHttpRequest.get("/").build();
        var exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = exchange1 -> Mono.empty();
        assertDoesNotThrow(() -> filter.apply(config).filter(exchange, chain).block());
    }
}
