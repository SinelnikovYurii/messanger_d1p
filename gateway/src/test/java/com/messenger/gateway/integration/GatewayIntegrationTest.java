package com.messenger.gateway.integration;

import com.messenger.gateway.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {
    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void testHealthEndpoint() {
        webTestClient.get()
                .uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("API Gateway is running");
    }

    @Test
    void testRootEndpoint() {
        webTestClient.get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("Messenger API Gateway");
    }

    @Test
    void testDebugStatusEndpoint() {
        webTestClient.get()
                .uri("/debug/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.gateway").isEqualTo("running")
                .jsonPath("$.port").isEqualTo(8080);
    }

    @Test
    void testDebugRoutesEndpoint() {
        webTestClient.get()
                .uri("/debug/routes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$['/ws/**']").isEqualTo("ws://localhost:8092")
                .jsonPath("$['/auth/**']").isEqualTo("http://localhost:8081")
                .jsonPath("$['/api/**']").isEqualTo("http://localhost:8083");
    }


}
