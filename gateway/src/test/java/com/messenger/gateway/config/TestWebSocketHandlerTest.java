package com.messenger.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class TestWebSocketHandlerTest {
    @Test
    void testHandle() {
        TestWebSocketHandler handler = new TestWebSocketHandler();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.receive()).thenReturn(Flux.empty());
        when(session.send(any())).thenReturn(Mono.empty());
        Mono<Void> result = handler.handle(session);
        assertNotNull(result);
        assertDoesNotThrow(() -> result.block());
    }
}
