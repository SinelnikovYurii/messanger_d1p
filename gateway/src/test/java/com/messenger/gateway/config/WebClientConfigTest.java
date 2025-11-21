package com.messenger.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import static org.junit.jupiter.api.Assertions.*;

class WebClientConfigTest {
    @Test
    void testWebClientBuilder() {
        WebClientConfig config = new WebClientConfig();
        WebClient.Builder builder = config.webClientBuilder();
        assertNotNull(builder);
        assertNotNull(builder.build());
    }
}

