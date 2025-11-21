package com.messenger.websocket.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import websocket.model.MessageType;
import websocket.model.WebSocketMessage;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class WebSocketMessageTest {
    @Test
    void testDefaultConstructorAndSetters() {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setId(1L);
        msg.setType(MessageType.CHAT_MESSAGE);
        msg.setContent("Hello");
        msg.setToken("token");
        msg.setChatId(2L);
        msg.setUserId(3L);
        msg.setSenderId(4L);
        msg.setUsername("user");
        msg.setSenderUsername("sender");
        LocalDateTime now = LocalDateTime.now();
        msg.setTimestamp(now);
        msg.setMessageType("TEXT");
        msg.setFileUrl("url");
        msg.setFileName("file.txt");
        msg.setFileSize(100L);
        msg.setMimeType("text/plain");
        msg.setThumbnailUrl("thumb");
        msg.setMessageId(5L);
        msg.setReaderId(6L);
        msg.setReaderUsername("reader");
        msg.setLastSeen(now);
        msg.setIsOnline(true);

        assertEquals(1L, msg.getId());
        assertEquals(MessageType.CHAT_MESSAGE, msg.getType());
        assertEquals("Hello", msg.getContent());
        assertEquals("token", msg.getToken());
        assertEquals(2L, msg.getChatId());
        assertEquals(3L, msg.getUserId());
        assertEquals(4L, msg.getSenderId());
        assertEquals("user", msg.getUsername());
        assertEquals("sender", msg.getSenderUsername());
        assertEquals(now, msg.getTimestamp());
        assertEquals("TEXT", msg.getMessageType());
        assertEquals("url", msg.getFileUrl());
        assertEquals("file.txt", msg.getFileName());
        assertEquals(100L, msg.getFileSize());
        assertEquals("text/plain", msg.getMimeType());
        assertEquals("thumb", msg.getThumbnailUrl());
        assertEquals(5L, msg.getMessageId());
        assertEquals(6L, msg.getReaderId());
        assertEquals("reader", msg.getReaderUsername());
        assertEquals(now, msg.getLastSeen());
        assertTrue(msg.getIsOnline());
    }

    @Test
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        WebSocketMessage msg = new WebSocketMessage(1L, MessageType.USER_ONLINE, "hi", "token", 2L, 3L, 4L, "user", "sender", now,
                "IMAGE", "url", "file.png", 200L, "image/png", "thumb", 5L, 6L, "reader", now, false);
        assertEquals(1L, msg.getId());
        assertEquals(MessageType.USER_ONLINE, msg.getType());
        assertEquals("hi", msg.getContent());
        assertEquals("token", msg.getToken());
        assertEquals(2L, msg.getChatId());
        assertEquals(3L, msg.getUserId());
        assertEquals(4L, msg.getSenderId());
        assertEquals("user", msg.getUsername());
        assertEquals("sender", msg.getSenderUsername());
        assertEquals(now, msg.getTimestamp());
        assertEquals("IMAGE", msg.getMessageType());
        assertEquals("url", msg.getFileUrl());
        assertEquals("file.png", msg.getFileName());
        assertEquals(200L, msg.getFileSize());
        assertEquals("image/png", msg.getMimeType());
        assertEquals("thumb", msg.getThumbnailUrl());
        assertEquals(5L, msg.getMessageId());
        assertEquals(6L, msg.getReaderId());
        assertEquals("reader", msg.getReaderUsername());
        assertEquals(now, msg.getLastSeen());
        assertFalse(msg.getIsOnline());
    }

    @Test
    void testQuickMessageConstructor() {
        WebSocketMessage msg = new WebSocketMessage(MessageType.USER_OFFLINE, "bye");
        assertEquals(MessageType.USER_OFFLINE, msg.getType());
        assertEquals("bye", msg.getContent());
        assertNotNull(msg.getTimestamp());
    }

    @Test
    void testChatMessageConstructor() {
        WebSocketMessage msg = new WebSocketMessage(MessageType.CHAT_MESSAGE, "msg", 10L, 20L, "userX");
        assertEquals(MessageType.CHAT_MESSAGE, msg.getType());
        assertEquals("msg", msg.getContent());
        assertEquals(10L, msg.getChatId());
        assertEquals(20L, msg.getUserId());
        assertEquals("userX", msg.getUsername());
        assertNotNull(msg.getTimestamp());
    }

    @Test
    void testTypeEnumJsonProperty() {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setTypeEnum(MessageType.USER_ONLINE);
        assertEquals(MessageType.USER_ONLINE, msg.getTypeEnum());
    }

    @Test
    void testEqualsHashCodeToString() {
        LocalDateTime now = LocalDateTime.now();
        WebSocketMessage msg1 = new WebSocketMessage(1L, MessageType.USER_ONLINE, "hi", "token", 2L, 3L, 4L, "user", "sender", now,
                "IMAGE", "url", "file.png", 200L, "image/png", "thumb", 5L, 6L, "reader", now, false);
        WebSocketMessage msg2 = new WebSocketMessage(1L, MessageType.USER_ONLINE, "hi", "token", 2L, 3L, 4L, "user", "sender", now,
                "IMAGE", "url", "file.png", 200L, "image/png", "thumb", 5L, 6L, "reader", now, false);
        assertEquals(msg1, msg2);
        assertEquals(msg1.hashCode(), msg2.hashCode());
        assertTrue(msg1.toString().contains("WebSocketMessage"));
    }

    @Test
    void testJacksonSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        WebSocketMessage msg = new WebSocketMessage(MessageType.CHAT_MESSAGE, "hello", 1L, 2L, "user");
        msg.setFileUrl("url");
        msg.setFileName("file.txt");
        msg.setFileSize(123L);
        msg.setMimeType("text/plain");
        msg.setThumbnailUrl("thumb");
        msg.setMessageId(5L);
        msg.setReaderId(6L);
        msg.setReaderUsername("reader");
        msg.setLastSeen(LocalDateTime.now());
        msg.setIsOnline(true);
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertEquals(msg.getContent(), deserialized.getContent());
        assertEquals(msg.getType(), deserialized.getType());
        assertEquals(msg.getChatId(), deserialized.getChatId());
        assertEquals(msg.getFileUrl(), deserialized.getFileUrl());
        assertEquals(msg.getFileName(), deserialized.getFileName());
        assertEquals(msg.getFileSize(), deserialized.getFileSize());
        assertEquals(msg.getMimeType(), deserialized.getMimeType());
        assertEquals(msg.getThumbnailUrl(), deserialized.getThumbnailUrl());
        assertEquals(msg.getMessageId(), deserialized.getMessageId());
        assertEquals(msg.getReaderId(), deserialized.getReaderId());
        assertEquals(msg.getReaderUsername(), deserialized.getReaderUsername());
        assertEquals(msg.getIsOnline(), deserialized.getIsOnline());
    }

    @Test
    void testNullFields() {
        WebSocketMessage msg = new WebSocketMessage();
        assertNull(msg.getId());
        assertNull(msg.getType());
        assertNull(msg.getContent());
        assertNull(msg.getToken());
        assertNull(msg.getChatId());
        assertNull(msg.getUserId());
        assertNull(msg.getSenderId());
        assertNull(msg.getUsername());
        assertNull(msg.getSenderUsername());
        assertNull(msg.getTimestamp());
        assertNull(msg.getMessageType());
        assertNull(msg.getFileUrl());
        assertNull(msg.getFileName());
        assertNull(msg.getFileSize());
        assertNull(msg.getMimeType());
        assertNull(msg.getThumbnailUrl());
        assertNull(msg.getMessageId());
        assertNull(msg.getReaderId());
        assertNull(msg.getReaderUsername());
        assertNull(msg.getLastSeen());
        assertNull(msg.getIsOnline());
    }

    @Test
    void testEmptyStringsAndZeroValues() {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setContent("");
        msg.setToken("");
        msg.setUsername("");
        msg.setSenderUsername("");
        msg.setMessageType("");
        msg.setFileUrl("");
        msg.setFileName("");
        msg.setMimeType("");
        msg.setThumbnailUrl("");
        msg.setReaderUsername("");
        msg.setId(0L);
        msg.setChatId(0L);
        msg.setUserId(0L);
        msg.setSenderId(0L);
        msg.setFileSize(0L);
        msg.setMessageId(0L);
        msg.setReaderId(0L);
        msg.setLastSeen(null);
        msg.setIsOnline(false);
        assertEquals("", msg.getContent());
        assertEquals("", msg.getToken());
        assertEquals("", msg.getUsername());
        assertEquals("", msg.getSenderUsername());
        assertEquals("", msg.getMessageType());
        assertEquals("", msg.getFileUrl());
        assertEquals("", msg.getFileName());
        assertEquals("", msg.getMimeType());
        assertEquals("", msg.getThumbnailUrl());
        assertEquals("", msg.getReaderUsername());
        assertEquals(0L, msg.getId());
        assertEquals(0L, msg.getChatId());
        assertEquals(0L, msg.getUserId());
        assertEquals(0L, msg.getSenderId());
        assertEquals(0L, msg.getFileSize());
        assertEquals(0L, msg.getMessageId());
        assertEquals(0L, msg.getReaderId());
        assertNull(msg.getLastSeen());
        assertFalse(msg.getIsOnline());
    }

    @Test
    void testNegativeValues() {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setId(-1L);
        msg.setChatId(-2L);
        msg.setUserId(-3L);
        msg.setSenderId(-4L);
        msg.setFileSize(-5L);
        msg.setMessageId(-6L);
        msg.setReaderId(-7L);
        assertEquals(-1L, msg.getId());
        assertEquals(-2L, msg.getChatId());
        assertEquals(-3L, msg.getUserId());
        assertEquals(-4L, msg.getSenderId());
        assertEquals(-5L, msg.getFileSize());
        assertEquals(-6L, msg.getMessageId());
        assertEquals(-7L, msg.getReaderId());
    }

    @Test
    void testEqualsDifferentObjects() {
        WebSocketMessage msg1 = new WebSocketMessage();
        WebSocketMessage msg2 = new WebSocketMessage();
        msg1.setId(1L);
        msg2.setId(2L);
        assertNotEquals(msg1, msg2);
        assertNotEquals(msg1.hashCode(), msg2.hashCode());
    }

    @Test
    void testJacksonUnknownFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String json = "{\"id\":1,\"type\":\"CHAT_MESSAGE\",\"unknownField\":\"value\"}";
        WebSocketMessage msg = mapper.readValue(json, WebSocketMessage.class);
        assertEquals(1L, msg.getId());
        assertEquals("CHAT_MESSAGE", msg.getType().name());
    }

    @Test
    void testBooleanIsOnline() {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setIsOnline(Boolean.TRUE);
        assertTrue(msg.getIsOnline());
        msg.setIsOnline(Boolean.FALSE);
        assertFalse(msg.getIsOnline());
    }

    @Test
    void testTimestampEdgeCases() {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setTimestamp(null);
        assertNull(msg.getTimestamp());
        LocalDateTime now = LocalDateTime.now();
        msg.setTimestamp(now);
        assertEquals(now, msg.getTimestamp());
    }

    @Test
    void testEqualsReflexive() {
        WebSocketMessage msg = new WebSocketMessage();
        assertEquals(msg, msg);
    }

    @Test
    void testEqualsNull() {
        WebSocketMessage msg = new WebSocketMessage();
        assertNotEquals(msg, null);
    }

    @Test
    void testEqualsOtherClass() {
        WebSocketMessage msg = new WebSocketMessage();
        Object other = new Object();
        assertNotEquals(msg, other);
    }

    @Test
    void testEqualsAllFieldsEqual() {
        LocalDateTime now = LocalDateTime.now();
        WebSocketMessage msg1 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 3L, 4L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        WebSocketMessage msg2 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 3L, 4L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        assertEquals(msg1, msg2);
    }

    @Test
    void testEqualsDifferentId() {
        LocalDateTime now = LocalDateTime.now();
        WebSocketMessage msg1 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 3L, 4L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        WebSocketMessage msg2 = new WebSocketMessage(2L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 3L, 4L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        assertNotEquals(msg1, msg2);
    }

    @Test
    void testEqualsDifferentType() {
        LocalDateTime now = LocalDateTime.now();
        WebSocketMessage msg1 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 3L, 4L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        WebSocketMessage msg2 = new WebSocketMessage(1L, MessageType.USER_ONLINE, "a", "t", 2L, 3L, 4L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        assertNotEquals(msg1, msg2);
    }

    @Test
    void testEqualsDifferentContent() {
        LocalDateTime now = LocalDateTime.now();
        WebSocketMessage msg1 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 3L, 4L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        WebSocketMessage msg2 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "b", "t", 2L, 3L, 4L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        assertNotEquals(msg1, msg2);
    }

    @Test
    void testEqualsDifferentToken() {
        LocalDateTime now = LocalDateTime.now();
        WebSocketMessage msg1 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t1", 2L, 3L, 4L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        WebSocketMessage msg2 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t2", 2L, 3L, 4L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        assertNotEquals(msg1, msg2);
    }

    @Test
    void testEqualsDifferentChatId() {
        LocalDateTime now = LocalDateTime.now();
        WebSocketMessage msg1 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 3L, 4L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        WebSocketMessage msg2 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 99L, 3L, 4L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        assertNotEquals(msg1, msg2);
    }

    @Test
    void testEqualsDifferentUserId() {
        LocalDateTime now = LocalDateTime.now();
        WebSocketMessage msg1 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 3L, 4L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        WebSocketMessage msg2 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 99L, 4L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        assertNotEquals(msg1, msg2);
    }

    @Test
    void testEqualsDifferentSenderId() {
        LocalDateTime now = LocalDateTime.now();
        WebSocketMessage msg1 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 3L, 4L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        WebSocketMessage msg2 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 3L, 99L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        assertNotEquals(msg1, msg2);
    }

    @Test
    void testEqualsDifferentUsername() {
        LocalDateTime now = LocalDateTime.now();
        WebSocketMessage msg1 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 3L, 4L, "u1", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        WebSocketMessage msg2 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 3L, 4L, "u2", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        assertNotEquals(msg1, msg2);
    }

    @Test
    void testEqualsDifferentSenderUsername() {
        LocalDateTime now = LocalDateTime.now();
        WebSocketMessage msg1 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 3L, 4L, "u", "su1", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        WebSocketMessage msg2 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 3L, 4L, "u", "su2", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        assertNotEquals(msg1, msg2);
    }

    @Test
    void testEqualsDifferentTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime later = now.plusSeconds(1);
        WebSocketMessage msg1 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 3L, 4L, "u", "su", now,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", now, true);
        WebSocketMessage msg2 = new WebSocketMessage(1L, MessageType.CHAT_MESSAGE, "a", "t", 2L, 3L, 4L, "u", "su", later,
                "TEXT", "url", "file", 100L, "mime", "thumb", 5L, 6L, "reader", later, true);
        assertNotEquals(msg1, msg2);
    }

    @Test
    void testEqualsNullFields() {
        WebSocketMessage msg1 = new WebSocketMessage();
        WebSocketMessage msg2 = new WebSocketMessage();
        assertEquals(msg1, msg2);
    }

    @Test
    void testEqualsDefaultValues() {
        WebSocketMessage msg1 = new WebSocketMessage();
        msg1.setId(0L);
        msg1.setChatId(0L);
        msg1.setUserId(0L);
        msg1.setSenderId(0L);
        msg1.setFileSize(0L);
        msg1.setMessageId(0L);
        msg1.setReaderId(0L);
        msg1.setIsOnline(false);
        WebSocketMessage msg2 = new WebSocketMessage();
        msg2.setId(0L);
        msg2.setChatId(0L);
        msg2.setUserId(0L);
        msg2.setSenderId(0L);
        msg2.setFileSize(0L);
        msg2.setMessageId(0L);
        msg2.setReaderId(0L);
        msg2.setIsOnline(false);
        assertEquals(msg1, msg2);
    }

    @Test
    void testEqualsEdgeValues() {
        LocalDateTime now = LocalDateTime.now();
        WebSocketMessage msg1 = new WebSocketMessage(Long.MAX_VALUE, MessageType.CHAT_MESSAGE, "", "", Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, "", "", now,
                "", "", "", Long.MAX_VALUE, "", "", Long.MIN_VALUE, Long.MAX_VALUE, "", now, false);
        WebSocketMessage msg2 = new WebSocketMessage(Long.MAX_VALUE, MessageType.CHAT_MESSAGE, "", "", Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, "", "", now,
                "", "", "", Long.MAX_VALUE, "", "", Long.MIN_VALUE, Long.MAX_VALUE, "", now, false);
        assertEquals(msg1, msg2);
    }
}
