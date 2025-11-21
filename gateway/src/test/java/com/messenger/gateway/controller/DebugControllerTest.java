package com.messenger.gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DebugControllerTest {
    @Test
    void testGetStatus() {
        DebugController controller = new DebugController();
        Mono<ResponseEntity<Map<String, Object>>> result = controller.getStatus();
        Map<String, Object> body = result.block().getBody();
        assertEquals("running", body.get("gateway"));
        assertEquals(8080, body.get("port"));
        assertTrue(body.containsKey("timestamp"));
    }

    @Test
    void testGetRoutes() {
        DebugController controller = new DebugController();
        Mono<ResponseEntity<Map<String, String>>> result = controller.getRoutes();
        Map<String, String> body = result.block().getBody();
        assertEquals("ws://localhost:8092", body.get("/ws/**"));
        assertEquals("http://localhost:8081", body.get("/auth/**"));
        assertEquals("http://localhost:8083", body.get("/api/**"));
    }
}

