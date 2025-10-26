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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageKafkaListener {

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

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

            // Если это NEW_MESSAGE - это уведомление о сообщении, которое УЖЕ сохранено в БД
            // Поэтому НЕ создаём дубликат
            if ("NEW_MESSAGE".equals(eventType)) {
                log.info("💡 [KAFKA] Received NEW_MESSAGE notification - message already saved in DB, skipping");
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

        } catch (Exception e) {
            log.error("❌ [KAFKA] Error processing chat message: {}", e.getMessage(), e);
            // В реальном приложении здесь можно добавить retry logic или dead letter queue
        }
    }
}
