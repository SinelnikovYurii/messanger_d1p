package com.messenger.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class JwtForwardingFilterFactoryTest {
    @Test
    void testApplyWithToken() {
        JwtForwardingFilterFactory factory = new JwtForwardingFilterFactory();
        JwtForwardingFilterFactory.Config config = new JwtForwardingFilterFactory.Config();
        var request = MockServerHttpRequest.get("/").header(HttpHeaders.AUTHORIZATION, "Bearer testtoken").build();
        var exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = exchange1 -> Mono.empty();
        assertDoesNotThrow(() -> factory.apply(config).filter(exchange, chain).block());
    }

    @Test
    void testApplyWithoutToken() {
        JwtForwardingFilterFactory factory = new JwtForwardingFilterFactory();
        JwtForwardingFilterFactory.Config config = new JwtForwardingFilterFactory.Config();
        var request = MockServerHttpRequest.get("/").build();
        var exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = exchange1 -> Mono.empty();
        assertDoesNotThrow(() -> factory.apply(config).filter(exchange, chain).block());
    }
}
