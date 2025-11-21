package com.messenger.core.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ChatTest {
    @Test
    void testPrePersistAndPreUpdate() {
        Chat chat = new Chat();
        chat.onCreate();
        assertNotNull(chat.getCreatedAt());
        assertNotNull(chat.getUpdatedAt());
        LocalDateTime created = chat.getCreatedAt();
        chat.onUpdate();
        assertTrue(chat.getUpdatedAt().isAfter(created) || chat.getUpdatedAt().isEqual(created));
    }

    @Test
    void testEqualsAndHashCode() {
        Chat chat1 = new Chat();
        chat1.setId(1L);
        chat1.setChatName("Test");
        chat1.setChatType(Chat.ChatType.GROUP);
        chat1.setParticipants(new HashSet<>());
        chat1.setMessages(new ArrayList<>());

        Chat chat2 = new Chat();
        chat2.setId(1L);
        chat2.setChatName("Test");
        chat2.setChatType(Chat.ChatType.GROUP);
        chat2.setParticipants(new HashSet<>());
        chat2.setMessages(new ArrayList<>());

        assertEquals(chat1, chat2);
        assertEquals(chat1.hashCode(), chat2.hashCode());
    }

    @Test
    void testAllArgsConstructorAndGettersSetters() {
        Chat chat = new Chat(1L, "Name", Chat.ChatType.PRIVATE, "desc", "url", LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, new HashSet<>(), new ArrayList<>());
        assertEquals(1L, chat.getId());
        assertEquals("Name", chat.getChatName());
        assertEquals(Chat.ChatType.PRIVATE, chat.getChatType());
        assertEquals("desc", chat.getChatDescription());
        assertEquals("url", chat.getChatAvatarUrl());
        assertNotNull(chat.getCreatedAt());
        assertNotNull(chat.getUpdatedAt());
        assertNotNull(chat.getLastMessageAt());
        assertNotNull(chat.getParticipants());
        assertNotNull(chat.getMessages());
    }

    @Test
    void testEnumValues() {
        assertEquals(Chat.ChatType.PRIVATE, Chat.ChatType.valueOf("PRIVATE"));
        assertEquals(Chat.ChatType.GROUP, Chat.ChatType.valueOf("GROUP"));
    }

    @Test
    void testEqualsWithNull() {
        Chat chat = new Chat();
        assertNotEquals(chat, null);
    }

    @Test
    void testEqualsWithOtherClass() {
        Chat chat = new Chat();
        assertNotEquals(chat, "string");
    }

    @Test
    void testEqualsWithSelf() {
        Chat chat = new Chat();
        assertEquals(chat, chat);
    }

    @Test
    void testEqualsWithDifferentFields() {
        Chat chat1 = new Chat();
        Chat chat2 = new Chat();
        chat1.setId(1L);
        chat2.setId(2L);
        assertNotEquals(chat1, chat2);
    }

    @Test
    void testEqualsWithSameFields() {
        Chat chat1 = new Chat();
        Chat chat2 = new Chat();
        chat1.setId(1L);
        chat2.setId(1L);
        chat1.setChatName("abc");
        chat2.setChatName("abc");
        chat1.setChatType(Chat.ChatType.GROUP);
        chat2.setChatType(Chat.ChatType.GROUP);
        assertEquals(chat1, chat2);
    }

    @Test
    void testEqualsWithDifferentChatName() {
        Chat c1 = new Chat();
        Chat c2 = new Chat();
        c1.setId(1L);
        c2.setId(1L);
        c1.setChatName("a");
        c2.setChatName("b");
        assertNotEquals(c1, c2);
    }

    @Test
    void testEqualsWithDifferentChatType() {
        Chat c1 = new Chat();
        Chat c2 = new Chat();
        c1.setId(1L);
        c2.setId(1L);
        c1.setChatType(Chat.ChatType.GROUP);
        c2.setChatType(Chat.ChatType.PRIVATE);
        assertNotEquals(c1, c2);
    }

    @Test
    void testEqualsWithDifferentChatDescription() {
        Chat c1 = new Chat();
        Chat c2 = new Chat();
        c1.setId(1L);
        c2.setId(1L);
        c1.setChatDescription("desc1");
        c2.setChatDescription("desc2");
        assertNotEquals(c1, c2);
    }

    @Test
    void testEqualsWithAllFieldsSame() {
        Chat c1 = new Chat();
        Chat c2 = new Chat();
        c1.setId(1L);
        c2.setId(1L);
        c1.setChatName("abc");
        c2.setChatName("abc");
        c1.setChatType(Chat.ChatType.GROUP);
        c2.setChatType(Chat.ChatType.GROUP);
        c1.setChatDescription("desc");
        c2.setChatDescription("desc");
        assertEquals(c1, c2);
    }

    @Test
    void testEqualsWithNullFields() {
        Chat c1 = new Chat();
        Chat c2 = new Chat();
        c1.setId(null);
        c2.setId(null);
        c1.setChatName(null);
        c2.setChatName(null);
        assertEquals(c1, c2);
    }

    @Test
    void testEqualsWithSameIdDifferentFields() {
        Chat c1 = new Chat();
        Chat c2 = new Chat();
        c1.setId(1L);
        c2.setId(1L);
        c1.setChatName(null);
        c2.setChatName("abc");
        assertNotEquals(c1, c2);
    }
}
