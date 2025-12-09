package com.messenger.websocket.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.messenger.websocket.service.SessionManager;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaMessageListener {

    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;

    @KafkaListener(topics = "chat-messages", groupId = "websocket-service-group")
    public void handleChatMessage(ConsumerRecord<String, String> record) {
        try {
            log.info("[KAFKA] Received message from Kafka: key={}, value={}", record.key(), record.value());


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
                log.info("[KAFKA] Broadcasting message to chat {}: {}", chatId, record.value());
                sessionManager.broadcastMessageToChat(chatId, messageData);
                log.info("[KAFKA] Message broadcast completed for chat {}", chatId);
            } else {
                log.warn("[KAFKA] Could not determine chatId for message: {}", record.value());
            }

        } catch (Exception e) {
            log.error("[KAFKA] Error processing message: {}", e.getMessage(), e);
        }
    }

    /**
     * Слушатель уведомлений о запросах в друзья и других WebSocket событий
     */
    @KafkaListener(topics = "websocket-notifications", groupId = "websocket-service-group")
    public void handleWebSocketNotification(ConsumerRecord<String, String> record) {
        try {
            log.info("[KAFKA-NOTIFICATION] Received notification from Kafka: key={}, value={}",
                record.key(), record.value());

            Map<String, Object> notificationData = objectMapper.readValue(record.value(), Map.class);
            String type = (String) notificationData.get("type");

            if (type == null) {
                log.warn("[KAFKA-NOTIFICATION] Notification type is missing");
                return;
            }

            // Получаем ID получателя уведомления
            Long recipientId = null;
            if (notificationData.containsKey("recipientId")) {
                recipientId = ((Number) notificationData.get("recipientId")).longValue();
            }

            if (recipientId != null) {
                log.info("[KAFKA-NOTIFICATION] Sending notification type '{}' to user {}", type, recipientId);
                sessionManager.sendNotificationToUser(recipientId, notificationData);
                log.info("[KAFKA-NOTIFICATION] Notification sent successfully");
            } else {
                log.warn("[KAFKA-NOTIFICATION] Could not determine recipientId for notification: {}",
                    record.value());
            }

        } catch (Exception e) {
            log.error("[KAFKA-NOTIFICATION] Error processing notification: {}", e.getMessage(), e);
        }
    }
}
