package com.messenger.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.core.model.Message;
import com.messenger.core.model.Chat;
import com.messenger.core.model.User;
import com.messenger.core.repository.MessageRepository;
import com.messenger.core.repository.ChatRepository;
import com.messenger.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageKafkaListener {

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "chat-messages", groupId = "core-api-service-group")
    @Transactional
    public void handleChatMessage(ConsumerRecord<String, String> record) {
        try {
            log.info("📨 [KAFKA] Received message from topic 'chat-messages': key={}, value={}",
                    record.key(), record.value());

            // Парсим сообщение из JSON
            Map<String, Object> messageData = objectMapper.readValue(record.value(), Map.class);

            // Проверяем тип события
            String eventType = (String) messageData.get("type");

            // ИСПРАВЛЕНИЕ: Игнорируем события MESSAGE_READ - они обрабатываются в MessageService
            if ("MESSAGE_READ".equals(eventType)) {
                log.info("📖 [KAFKA] Received MESSAGE_READ event - skipping (handled by MessageService)");
                return;
            }

            // ИСПРАВЛЕНИЕ: Игнорируем события MESSAGE_UPDATE
            if ("MESSAGE_UPDATE".equals(eventType)) {
                log.info("✏️ [KAFKA] Received MESSAGE_UPDATE event - skipping (already processed)");
                return;
            }

            // ИСПРАВЛЕНИЕ: Игнорируем события CHAT_MESSAGE - это уведомление о сообщении, которое УЖЕ сохранено в БД
            if ("CHAT_MESSAGE".equals(eventType)) {
                log.info("💡 [KAFKA] Received CHAT_MESSAGE notification - message already saved in DB, skipping");
                return;
            }

            // Извлекаем данные (только для сообщений от WebSocket, которые ещё не сохранены)
            Long senderId = ((Number) messageData.get("senderId")).longValue();
            Long chatId = ((Number) messageData.get("chatId")).longValue();
            String content = (String) messageData.get("content");
            String messageType = (String) messageData.get("messageType");

            log.info("💾 [DB] Saving message to database - senderId: {}, chatId: {}, content: '{}'",
                    senderId, chatId, content);

            // Проверяем существование чата и пользователя
            Chat chat = chatRepository.findById(chatId)
                    .orElseThrow(() -> new RuntimeException("Chat not found: " + chatId));

            User sender = userRepository.findById(senderId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + senderId));

            // Создаем и сохраняем сообщение
            Message message = new Message();
            message.setContent(content);
            message.setMessageType(Message.MessageType.valueOf(messageType));
            message.setChat(chat);
            message.setSender(sender);
            message.setCreatedAt(LocalDateTime.now());

            Message savedMessage = messageRepository.save(message);

            log.info("✅ [DB] Message saved successfully with ID: {}", savedMessage.getId());

            // ИСПРАВЛЕНИЕ: Отправляем уведомление в Kafka с реальным ID сообщения
            notifyAboutNewMessage(savedMessage);

        } catch (Exception e) {
            log.error("❌ [KAFKA] Error processing chat message: {}", e.getMessage(), e);
            // В реальном приложении здесь можно добавить retry logic или dead letter queue
        }
    }

    /**
     * Уведомить о новом сообщении через Kafka с реальным ID из БД
     */
    private void notifyAboutNewMessage(Message message) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "CHAT_MESSAGE");
        notification.put("id", message.getId()); // Реальный ID из БД
        notification.put("messageId", message.getId());
        notification.put("chatId", message.getChat().getId());
        notification.put("senderId", message.getSender().getId());
        notification.put("senderUsername", message.getSender().getUsername());
        notification.put("content", message.getContent());
        notification.put("messageType", message.getMessageType().toString());
        notification.put("timestamp", message.getCreatedAt());

        // Добавляем метаданные файлов если есть
        if (message.getFileUrl() != null) {
            notification.put("fileUrl", message.getFileUrl());
            notification.put("fileName", message.getFileName());
            notification.put("fileSize", message.getFileSize());
            notification.put("mimeType", message.getMimeType());
            notification.put("thumbnailUrl", message.getThumbnailUrl());
        }

        log.info("📤 [KAFKA] Sending CHAT_MESSAGE notification with ID: {} to Kafka", message.getId());
        kafkaTemplate.send("chat-messages", message.getChat().getId().toString(), notification);
    }
}
