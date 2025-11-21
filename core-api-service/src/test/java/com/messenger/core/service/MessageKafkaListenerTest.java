package com.messenger.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.core.model.Chat;
import com.messenger.core.model.Message;
import com.messenger.core.model.User;
import com.messenger.core.repository.ChatRepository;
import com.messenger.core.repository.MessageRepository;
import com.messenger.core.repository.UserRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageKafkaListenerTest {
    private MessageRepository messageRepository;
    private ChatRepository chatRepository;
    private UserRepository userRepository;
    private ObjectMapper objectMapper;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private MessageKafkaListener listener;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        chatRepository = mock(ChatRepository.class);
        userRepository = mock(UserRepository.class);
        objectMapper = new ObjectMapper();
        kafkaTemplate = mock(KafkaTemplate.class);
        listener = new MessageKafkaListener(messageRepository, chatRepository, userRepository, objectMapper, kafkaTemplate);
    }

    @Test
    void testHandleChatMessage_messageReadEvent_skipped() {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "MESSAGE_READ");
        String json = toJson(data);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("chat-messages", 0, 0L, "key", json);
        listener.handleChatMessage(record);
        verifyNoInteractions(messageRepository);
        verifyNoInteractions(chatRepository);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void testHandleChatMessage_messageUpdateEvent_skipped() {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "MESSAGE_UPDATE");
        String json = toJson(data);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("chat-messages", 0, 0L, "key", json);
        listener.handleChatMessage(record);
        verifyNoInteractions(messageRepository);
        verifyNoInteractions(chatRepository);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void testHandleChatMessage_chatMessageNotification_skipped() {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "CHAT_MESSAGE");
        String json = toJson(data);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("chat-messages", 0, 0L, "key", json);
        listener.handleChatMessage(record);
        verifyNoInteractions(messageRepository);
        verifyNoInteractions(chatRepository);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void testHandleChatMessage_successfulSaveAndNotify() {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "NEW_MESSAGE");
        data.put("senderId", 1L);
        data.put("chatId", 2L);
        data.put("content", "Hello");
        data.put("messageType", "TEXT");
        String json = toJson(data);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("chat-messages", 0, 0L, "key", json);

        Chat chat = new Chat();
        chat.setId(2L);
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        when(chatRepository.findById(2L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Message savedMessage = new Message();
        savedMessage.setId(100L);
        savedMessage.setContent("Hello");
        savedMessage.setMessageType(Message.MessageType.TEXT);
        savedMessage.setChat(chat);
        savedMessage.setSender(user);
        savedMessage.setCreatedAt(LocalDateTime.now());
        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

        listener.handleChatMessage(record);

        verify(messageRepository).save(any(Message.class));
        verify(kafkaTemplate).send(eq("chat-messages"), eq("2"), any(Map.class));
    }

    @Test
    void testHandleChatMessage_chatNotFound() {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "NEW_MESSAGE");
        data.put("senderId", 1L);
        data.put("chatId", 999L);
        data.put("content", "Hello");
        data.put("messageType", "TEXT");
        String json = toJson(data);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("chat-messages", 0, 0L, "key", json);
        when(chatRepository.findById(999L)).thenReturn(Optional.empty());
        listener.handleChatMessage(record);
        verify(chatRepository).findById(999L);
        verifyNoInteractions(messageRepository);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void testHandleChatMessage_userNotFound() {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "NEW_MESSAGE");
        data.put("senderId", 999L);
        data.put("chatId", 2L);
        data.put("content", "Hello");
        data.put("messageType", "TEXT");
        String json = toJson(data);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("chat-messages", 0, 0L, "key", json);
        Chat chat = new Chat();
        chat.setId(2L);
        when(chatRepository.findById(2L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        listener.handleChatMessage(record);
        verify(chatRepository).findById(2L);
        verify(userRepository).findById(999L);
        verifyNoInteractions(messageRepository);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void testHandleChatMessage_invalidJson() {
        String invalidJson = "{invalid_json}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("chat-messages", 0, 0L, "key", invalidJson);
        listener.handleChatMessage(record);
        verifyNoInteractions(messageRepository);
        verifyNoInteractions(chatRepository);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(kafkaTemplate);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

