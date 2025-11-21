package com.messenger.gateway.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KafkaConfigTest {
    @Test
    void testKafkaConfigExists() {
        KafkaConfig config = new KafkaConfig();
        assertNotNull(config);
    }
}

