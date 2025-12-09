package com.messenger.websocket.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import com.messenger.websocket.config.KafkaProducerConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KafkaProducerConfigTest {
    private KafkaProducerConfig config;

    @BeforeEach
    void setUp() {
        config = new KafkaProducerConfig();
        config.setBootstrapServers("localhost:9092");
    }

    @Test
    void producerFactory_shouldCreateFactoryWithCorrectConfig() {
        ProducerFactory<String, String> factory = config.producerFactory();
        assertNotNull(factory);
        assertInstanceOf(DefaultKafkaProducerFactory.class, factory);
        DefaultKafkaProducerFactory<String, String> propsFactory = (DefaultKafkaProducerFactory<String, String>) factory;
        Map<String, Object> props = propsFactory.getConfigurationProperties();
        assertEquals("localhost:9092", props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals(StringSerializer.class, props.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
        assertEquals(StringSerializer.class, props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertEquals("all", props.get(ProducerConfig.ACKS_CONFIG));
        assertEquals(3, props.get(ProducerConfig.RETRIES_CONFIG));
        assertEquals(16384, props.get(ProducerConfig.BATCH_SIZE_CONFIG));
        assertEquals(1, props.get(ProducerConfig.LINGER_MS_CONFIG));
        assertEquals(33554432, props.get(ProducerConfig.BUFFER_MEMORY_CONFIG));
    }

    @Test
    void kafkaTemplate_shouldCreateTemplate() {
        KafkaTemplate<String, String> template = config.kafkaTemplate();
        assertNotNull(template);
    }

    @Test
    void producerFactory_withInvalidBootstrapServers_shouldStillCreateFactory() {
        config.setBootstrapServers("");
        ProducerFactory<String, String> factory = config.producerFactory();
        assertNotNull(factory);
        DefaultKafkaProducerFactory<String, String> propsFactory2 = (DefaultKafkaProducerFactory<String, String>) factory;
        Map<String, Object> props2 = propsFactory2.getConfigurationProperties();
        assertEquals("", props2.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
    }

    @Test
    void kafkaTemplate_withNullBootstrapServers_shouldThrowExceptionOnSend() {
        config.setBootstrapServers(null);
        KafkaTemplate<String, String> template = config.kafkaTemplate();
        assertThrows(Exception.class, () -> {
            template.send("test-topic", "key", "value").get();
        });
    }
}
