package com.messenger.gateway.exception;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class GlobalErrorHandlerTest {
    @Test
    void testHandleException() {
        GlobalErrorHandler handler = new GlobalErrorHandler();
        var request = MockServerHttpRequest.get("/").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        Throwable ex = new RuntimeException("Test error");
        Mono<Void> result = handler.handle(exchange, ex);
        assertNotNull(result);
    }
}
