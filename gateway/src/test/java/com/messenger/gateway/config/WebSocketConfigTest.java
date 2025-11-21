package com.messenger.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import static org.junit.jupiter.api.Assertions.*;

class WebSocketConfigTest {
    @Test
    void testWebSocketHandlerMapping() {
        WebSocketConfig config = new WebSocketConfig();
        HandlerMapping mapping = config.webSocketHandlerMapping();
        assertNotNull(mapping);
    }

    @Test
    void testHandlerAdapter() {
        WebSocketConfig config = new WebSocketConfig();
        WebSocketHandlerAdapter adapter = config.handlerAdapter();
        assertNotNull(adapter);
    }
}

