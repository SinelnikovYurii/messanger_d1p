package com.messenger.gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class HealthControllerTest {
    @Test
    void testHealth() {
        HealthController controller = new HealthController();
        Mono<ResponseEntity<String>> result = controller.health();
        assertEquals("API Gateway is running", result.block().getBody());
    }

    @Test
    void testRoot() {
        HealthController controller = new HealthController();
        Mono<ResponseEntity<String>> result = controller.root();
        assertEquals("Messenger API Gateway", result.block().getBody());
    }
}

