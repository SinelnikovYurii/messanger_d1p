package com.messenger.websocket.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.messenger.websocket.kafka.KafkaMessageListener;
import com.messenger.websocket.service.SessionManager;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

class KafkaMessageListenerTest {
    private ObjectMapper objectMapper;
    private SessionManager sessionManager;
    private KafkaMessageListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = mock(ObjectMapper.class);
        sessionManager = mock(SessionManager.class);
        listener = new KafkaMessageListener(objectMapper, sessionManager);
    }

    @Test
    void testHandleChatMessageWithValidKey() throws Exception {
        ConsumerRecord<String, String> record = mock(ConsumerRecord.class);
        when(record.key()).thenReturn("123");
        when(record.value()).thenReturn("{\"chatId\":123,\"msg\":\"hi\"}");
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chatId", 123L);
        messageData.put("msg", "hi");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(messageData);
        listener.handleChatMessage(record);
        verify(sessionManager).broadcastMessageToChat(eq(123L), eq(messageData));
    }

    @Test
    void testHandleChatMessageWithInvalidKeyUsesMessageDataChatId() throws Exception {
        ConsumerRecord<String, String> record = mock(ConsumerRecord.class);
        when(record.key()).thenReturn("not_a_number");
        when(record.value()).thenReturn("{\"chatId\":456,\"msg\":\"hi\"}");
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chatId", 456L);
        messageData.put("msg", "hi");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(messageData);
        listener.handleChatMessage(record);
        verify(sessionManager).broadcastMessageToChat(eq(456L), eq(messageData));
    }

    @Test
    void testHandleChatMessageNoKeyUsesMessageDataChatId() throws Exception {
        ConsumerRecord<String, String> record = mock(ConsumerRecord.class);
        when(record.key()).thenReturn(null);
        when(record.value()).thenReturn("{\"chatId\":789,\"msg\":\"hi\"}");
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chatId", 789L);
        messageData.put("msg", "hi");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(messageData);
        listener.handleChatMessage(record);
        verify(sessionManager).broadcastMessageToChat(eq(789L), eq(messageData));
    }

    @Test
    void testHandleChatMessageNoKeyNoChatId() throws Exception {
        ConsumerRecord<String, String> record = mock(ConsumerRecord.class);
        when(record.key()).thenReturn(null);
        when(record.value()).thenReturn("{\"msg\":\"hi\"}");
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("msg", "hi");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(messageData);
        listener.handleChatMessage(record);
        verify(sessionManager, never()).broadcastMessageToChat(anyLong(), any());
    }

    @Test
    void testHandleChatMessageInvalidJson() throws Exception {
        ConsumerRecord<String, String> record = mock(ConsumerRecord.class);
        when(record.key()).thenReturn("123");
        when(record.value()).thenReturn("invalid json");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenThrow(new RuntimeException("json error"));
        listener.handleChatMessage(record);
        verify(sessionManager, never()).broadcastMessageToChat(anyLong(), any());
    }

    @Test
    void testHandleChatMessageChatIdNotNumber() throws Exception {
        ConsumerRecord<String, String> record = mock(ConsumerRecord.class);
        when(record.key()).thenReturn(null);
        when(record.value()).thenReturn("{\"chatId\":\"not_a_number\"}");
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("chatId", "not_a_number");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(messageData);
        listener.handleChatMessage(record);
        verify(sessionManager, never()).broadcastMessageToChat(anyLong(), any());
    }

    @Test
    void testHandleWebSocketNotificationValid() throws Exception {
        ConsumerRecord<String, String> record = mock(ConsumerRecord.class);
        when(record.key()).thenReturn("key");
        when(record.value()).thenReturn("{\"type\":\"FRIEND_REQUEST\",\"recipientId\":321}");
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "FRIEND_REQUEST");
        notificationData.put("recipientId", 321L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(notificationData);
        listener.handleWebSocketNotification(record);
        verify(sessionManager).sendNotificationToUser(eq(321L), eq(notificationData));
    }

    @Test
    void testHandleWebSocketNotificationNoType() throws Exception {
        ConsumerRecord<String, String> record = mock(ConsumerRecord.class);
        when(record.key()).thenReturn("key");
        when(record.value()).thenReturn("{\"recipientId\":321}");
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("recipientId", 321L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(notificationData);
        listener.handleWebSocketNotification(record);
        verify(sessionManager, never()).sendNotificationToUser(anyLong(), any());
    }

    @Test
    void testHandleWebSocketNotificationNoRecipientId() throws Exception {
        ConsumerRecord<String, String> record = mock(ConsumerRecord.class);
        when(record.key()).thenReturn("key");
        when(record.value()).thenReturn("{\"type\":\"FRIEND_REQUEST\"}");
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "FRIEND_REQUEST");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(notificationData);
        listener.handleWebSocketNotification(record);
        verify(sessionManager, never()).sendNotificationToUser(anyLong(), any());
    }

    @Test
    void testHandleWebSocketNotificationInvalidJson() throws Exception {
        ConsumerRecord<String, String> record = mock(ConsumerRecord.class);
        when(record.key()).thenReturn("key");
        when(record.value()).thenReturn("invalid json");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenThrow(new RuntimeException("json error"));
        listener.handleWebSocketNotification(record);
        verify(sessionManager, never()).sendNotificationToUser(anyLong(), any());
    }

    @Test
    void testHandleWebSocketNotificationRecipientIdNotNumber() throws Exception {
        ConsumerRecord<String, String> record = mock(ConsumerRecord.class);
        when(record.key()).thenReturn("key");
        when(record.value()).thenReturn("{\"type\":\"FRIEND_REQUEST\",\"recipientId\":\"not_a_number\"}");
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "FRIEND_REQUEST");
        notificationData.put("recipientId", "not_a_number");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(notificationData);
        listener.handleWebSocketNotification(record);
        verify(sessionManager, never()).sendNotificationToUser(anyLong(), any());
    }
}
