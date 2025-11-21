package com.messenger.websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import websocket.model.MessageType;
import websocket.model.WebSocketMessage;
import websocket.service.MessageForwardService;
import websocket.service.SessionManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MessageForwardServiceTest {
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private Channel channel;

    private MessageForwardService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new MessageForwardService(objectMapper, sessionManager);
    }

    @Test
    void testHandleMessageFromKafkaValidMessage() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("content", "Hello");
        msgData.put("chatId", 1L);
        msgData.put("senderId", 2L);
        msgData.put("senderUsername", "user");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
        verify(channel, atLeastOnce()).writeAndFlush(any(TextWebSocketFrame.class));
    }

    @Test
    void testHandleMessageFromKafkaNoChatId() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        service.handleMessageFromKafka(null, "{}");
        // Должен просто завершиться без ошибок
    }

    @Test
    void testHandleMessageFromKafkaInvalidKey() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", "not_a_number");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        service.handleMessageFromKafka("not_a_number", "{}");
    }

    @Test
    void testHandleMessageFromKafkaUnknownMessageType() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "UNKNOWN_TYPE");
        msgData.put("chatId", 1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
        verify(channel, atLeastOnce()).writeAndFlush(any(TextWebSocketFrame.class));
    }

    @Test
    void testHandleMessageFromKafkaMessageReadType() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "MESSAGE_READ");
        msgData.put("chatId", 1L);
        msgData.put("messageId", "123");
        msgData.put("readerId", "456");
        msgData.put("readerUsername", "reader");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
        verify(channel, atLeastOnce()).writeAndFlush(any(TextWebSocketFrame.class));
    }

    @Test
    void testHandleMessageFromKafkaWithFileMeta() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("chatId", 1L);
        msgData.put("fileUrl", "url");
        msgData.put("fileName", "name");
        msgData.put("fileSize", "1000");
        msgData.put("mimeType", "type");
        msgData.put("thumbnailUrl", "thumb");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
        verify(channel, atLeastOnce()).writeAndFlush(any(TextWebSocketFrame.class));
    }

    @Test
    void testHandleMessageFromKafkaNoChannels() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("chatId", 1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(Collections.emptyList());
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaInactiveChannel() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("chatId", 1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(false);
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaChannelWriteException() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("chatId", 1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        doThrow(new RuntimeException("write error")).when(channel).writeAndFlush(any());
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaObjectMapperException() throws Exception {
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenThrow(new RuntimeException("parse error"));
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaChatIdAsString() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("chatId", "1");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
        verify(channel, atLeastOnce()).writeAndFlush(any(TextWebSocketFrame.class));
    }

    @Test
    void testHandleMessageFromKafkaChatIdAsInvalidString() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("chatId", "not_a_number");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        service.handleMessageFromKafka("not_a_number", "{}");
    }

    @Test
    void testHandleMessageFromKafkaMessageIdAsString() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "MESSAGE_READ");
        msgData.put("chatId", 1L);
        msgData.put("messageId", "123");
        msgData.put("readerId", 456L);
        msgData.put("readerUsername", "reader");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
        verify(channel, atLeastOnce()).writeAndFlush(any(TextWebSocketFrame.class));
    }

    @Test
    void testHandleMessageFromKafkaSenderIdAsString() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("chatId", 1L);
        msgData.put("senderId", "2");
        msgData.put("senderUsername", "user");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
        verify(channel, atLeastOnce()).writeAndFlush(any(TextWebSocketFrame.class));
    }

    @Test
    void testHandleMessageFromKafkaChannelIdThrowsException() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("chatId", 1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(channel.id()).thenThrow(new RuntimeException("id error"));
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaChannelIsActiveThrowsException() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("chatId", 1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenThrow(new RuntimeException("active error"));
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaChannelIsNull() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("chatId", 1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        List<Channel> channels = new java.util.ArrayList<>();
        channels.add(null);
        when(sessionManager.getChatChannels(1L)).thenReturn(channels);
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaBodyIsNull() throws Exception {
        service.handleMessageFromKafka("1", null);
    }

    @Test
    void testHandleMessageFromKafkaKeyAndBodyNull() throws Exception {
        service.handleMessageFromKafka(null, null);
    }

    @Test
    void testHandleMessageFromKafkaFileSizeInvalidType() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("chatId", 1L);
        msgData.put("fileSize", new Object());
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
        verify(channel, atLeastOnce()).writeAndFlush(any(TextWebSocketFrame.class));
    }

    @Test
    void testHandleMessageFromKafkaMessageTypeInvalid() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", 12345);
        msgData.put("chatId", 1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
        verify(channel, atLeastOnce()).writeAndFlush(any(TextWebSocketFrame.class));
    }

    @Test
    void testHandleMessageFromKafkaPayloadNotSerializable() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("chatId", 1L);
        msgData.put("payload", new Object());
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        doThrow(new RuntimeException("serialization error")).when(objectMapper).writeValueAsString(any());
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaSessionManagerThrowsException() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("chatId", 1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenThrow(new RuntimeException("session error"));
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaChannelWriteAndFlushThrowsException() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("chatId", 1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        doThrow(new RuntimeException("write error")).when(channel).writeAndFlush(any());
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaObjectMapperWriteValueThrowsException() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("chatId", 1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        doThrow(new RuntimeException("writeValue error")).when(objectMapper).writeValueAsString(any());
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaObjectMapperReturnsNull() throws Exception {
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(null);
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaEmptyMap() throws Exception {
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(new HashMap<>());
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaChatIdNoType() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaTypeNull() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("type", null);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaTypeEmptyString() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("type", "");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaChatIdNullKeyValid() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", null);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaSenderIdNull() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("senderId", null);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaSenderUsernameNull() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("senderUsername", null);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaFileFieldsNull() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("fileUrl", null);
        msgData.put("fileName", null);
        msgData.put("fileSize", null);
        msgData.put("mimeType", null);
        msgData.put("thumbnailUrl", null);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaIdAndMessageIdNull() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("id", null);
        msgData.put("messageId", null);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaMessageReadReaderFieldsNull() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "MESSAGE_READ");
        msgData.put("chatId", 1L);
        msgData.put("readerId", null);
        msgData.put("readerUsername", null);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaSenderIdBoolean() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("senderId", Boolean.TRUE);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaFileSizeBoolean() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("fileSize", Boolean.TRUE);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaIdBoolean() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("id", Boolean.TRUE);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaMessageIdBoolean() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("messageId", Boolean.TRUE);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaReaderIdBoolean() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "MESSAGE_READ");
        msgData.put("chatId", 1L);
        msgData.put("readerId", Boolean.TRUE);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaChatIdBoolean() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", Boolean.TRUE);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaExtraKeys() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("type", "CHAT_MESSAGE");
        msgData.put("extra1", "value1");
        msgData.put("extra2", "value2");
        msgData.put("extra3", "value3");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaPayloadNull() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("payload", null);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaMessageTypeNull() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("messageType", null);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaMessageTypeEmptyString() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("messageType", "");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaMessageTypeUnknown() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("messageType", "UNKNOWN_TYPE");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaFileSizeVeryLarge() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("fileSize", Long.MAX_VALUE);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaFileSizeNegative() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("fileSize", -100L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaChatIdZero() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 0L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(0L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("0", "{}");
    }

    @Test
    void testHandleMessageFromKafkaChatIdNegative() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", -1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(-1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("-1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaSenderIdZero() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("senderId", 0L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaSenderIdNegative() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("senderId", -1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaIdZero() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("id", 0L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaIdNegative() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("id", -1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaMessageIdZero() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("messageId", 0L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaMessageIdNegative() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("chatId", 1L);
        msgData.put("messageId", -1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaReaderIdZero() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "MESSAGE_READ");
        msgData.put("chatId", 1L);
        msgData.put("readerId", 0L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }

    @Test
    void testHandleMessageFromKafkaReaderIdNegative() throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "MESSAGE_READ");
        msgData.put("chatId", 1L);
        msgData.put("readerId", -1L);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(msgData);
        when(sessionManager.getChatChannels(1L)).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        service.handleMessageFromKafka("1", "{}");
    }
}
