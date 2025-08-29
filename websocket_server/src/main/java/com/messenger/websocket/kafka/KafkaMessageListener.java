package websocket.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import websocket.service.SessionManager;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaMessageListener {

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "chat-messages", groupId = "websocket-service-group")
    public void handleChatMessage(ConsumerRecord<String, String> record) {
        try {
            log.debug("Received message from Kafka: key={}, value={}", record.key(), record.value());


            Map<String, Object> messageData = objectMapper.readValue(record.value(), Map.class);


            Long chatId = null;
            if (record.key() != null && !record.key().isEmpty()) {
                try {
                    chatId = Long.valueOf(record.key());
                } catch (NumberFormatException e) {
                    log.warn("Invalid key format: {}", record.key());
                }
            }

            if (chatId == null && messageData.containsKey("chatId")) {
                chatId = ((Number) messageData.get("chatId")).longValue();
            }

            if (chatId != null) {

                SessionManager.broadcastMessageToChat(chatId, messageData);
                log.info("Broadcasted message to chat {}: {}", chatId, record.value());
            } else {
                log.warn("Could not determine chatId for message: {}", record.value());
            }

        } catch (Exception e) {
            log.error("Error processing Kafka message: {}", record.value(), e);
        }
    }
}
