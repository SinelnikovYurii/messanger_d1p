package com.messenger.core.dto;

import com.messenger.core.model.Chat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.Collections;

class ChatDtoTest {
    @Test
    void testGettersAndSetters() {
        ChatDto dto = new ChatDto();
        dto.setId(1L);
        dto.setChatName("chat");
        dto.setChatDescription("desc");
        dto.setChatAvatarUrl("avatar");
        LocalDateTime now = LocalDateTime.now();
        dto.setCreatedAt(now);
        dto.setLastMessageAt(now);
        dto.setUnreadCount(3);
        dto.setParticipants(Collections.emptyList());
        assertEquals(1L, dto.getId());
        assertEquals("chat", dto.getChatName());
        assertEquals("desc", dto.getChatDescription());
        assertEquals("avatar", dto.getChatAvatarUrl());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getLastMessageAt());
        assertEquals(3, dto.getUnreadCount());
        assertEquals(Collections.emptyList(), dto.getParticipants());
    }

    @Test
    void testEqualsAndHashCode() {
        ChatDto dto1 = new ChatDto(1L, "chat", null, "desc", "avatar", LocalDateTime.now(), LocalDateTime.now(), null, Collections.emptyList(), null, 3);
        ChatDto dto2 = new ChatDto(1L, "chat", null, "desc", "avatar", dto1.getCreatedAt(), dto1.getLastMessageAt(), null, Collections.emptyList(), null, 3);
        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void testToString() {
        ChatDto dto = new ChatDto(1L, "chat", null, "desc", "avatar", LocalDateTime.now(), LocalDateTime.now(), null, Collections.emptyList(), null, 3);
        String str = dto.toString();
        assertTrue(str.contains("chat"));
        assertTrue(str.contains("desc"));
    }

    @Test
    void testCreateChatRequestDto() {
        ChatDto.CreateChatRequest dto = new ChatDto.CreateChatRequest("chat", null, "desc", java.util.Collections.singletonList(1L));
        assertEquals("chat", dto.getChatName());
        assertEquals("desc", dto.getChatDescription());
        assertEquals(java.util.Collections.singletonList(1L), dto.getParticipantIds());
    }

    @Test
    void testAddParticipantsRequestDto() {
        ChatDto.AddParticipantsRequest dto = new ChatDto.AddParticipantsRequest(2L, java.util.Collections.singletonList(3L));
        assertEquals(2L, dto.getChatId());
        assertEquals(java.util.Collections.singletonList(3L), dto.getUserIds());
    }

    @Test
    void testCreatePrivateChatRequestDto() {
        ChatDto.CreatePrivateChatRequest dto = new ChatDto.CreatePrivateChatRequest(4L);
        assertEquals(4L, dto.getParticipantId());
    }

    @Test
    void testCreateChatRequestEqualsAndHashCode() {
        ChatDto.CreateChatRequest dto1 = new ChatDto.CreateChatRequest("chat", null, "desc", java.util.Collections.singletonList(1L));
        ChatDto.CreateChatRequest dto2 = new ChatDto.CreateChatRequest("chat", null, "desc", java.util.Collections.singletonList(1L));
        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void testCreateChatRequestToString() {
        ChatDto.CreateChatRequest dto = new ChatDto.CreateChatRequest("chat", null, "desc", java.util.Collections.singletonList(1L));
        assertTrue(dto.toString().contains("chat"));
    }

    @Test
    void testAddParticipantsRequestEqualsAndHashCode() {
        ChatDto.AddParticipantsRequest dto1 = new ChatDto.AddParticipantsRequest(2L, java.util.Collections.singletonList(3L));
        ChatDto.AddParticipantsRequest dto2 = new ChatDto.AddParticipantsRequest(2L, java.util.Collections.singletonList(3L));
        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void testAddParticipantsRequestToString() {
        ChatDto.AddParticipantsRequest dto = new ChatDto.AddParticipantsRequest(2L, java.util.Collections.singletonList(3L));
        assertTrue(dto.toString().contains("2"));
    }

    @Test
    void testCreatePrivateChatRequestEqualsAndHashCode() {
        ChatDto.CreatePrivateChatRequest dto1 = new ChatDto.CreatePrivateChatRequest(4L);
        ChatDto.CreatePrivateChatRequest dto2 = new ChatDto.CreatePrivateChatRequest(4L);
        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void testCreatePrivateChatRequestToString() {
        ChatDto.CreatePrivateChatRequest dto = new ChatDto.CreatePrivateChatRequest(4L);
        assertTrue(dto.toString().contains("4"));
    }

    @Test
    void testCreateChatRequestNullArguments() {
        ChatDto.CreateChatRequest dto = new ChatDto.CreateChatRequest(null, null, null, null);
        assertNull(dto.getChatName());
        assertNull(dto.getChatType());
        assertNull(dto.getChatDescription());
        assertNull(dto.getParticipantIds());
    }

    @Test
    void testCreateChatRequestEqualsWithDifferentObjects() {
        ChatDto.CreateChatRequest dto1 = new ChatDto.CreateChatRequest("chat1", null, "desc1", java.util.Collections.singletonList(1L));
        ChatDto.CreateChatRequest dto2 = new ChatDto.CreateChatRequest("chat2", null, "desc2", java.util.Collections.singletonList(2L));
        assertNotEquals(dto1, dto2);
        assertNotEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void testAddParticipantsRequestEqualsWithDifferentObjects() {
        ChatDto.AddParticipantsRequest dto1 = new ChatDto.AddParticipantsRequest(1L, java.util.Collections.singletonList(2L));
        ChatDto.AddParticipantsRequest dto2 = new ChatDto.AddParticipantsRequest(2L, java.util.Collections.singletonList(3L));
        assertNotEquals(dto1, dto2);
        assertNotEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void testCreatePrivateChatRequestEqualsWithDifferentObjects() {
        ChatDto.CreatePrivateChatRequest dto1 = new ChatDto.CreatePrivateChatRequest(1L);
        ChatDto.CreatePrivateChatRequest dto2 = new ChatDto.CreatePrivateChatRequest(2L);
        assertNotEquals(dto1, dto2);
        assertNotEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void testCreateChatRequestToStringWithNullFields() {
        ChatDto.CreateChatRequest dto = new ChatDto.CreateChatRequest(null, null, null, null);
        String str = dto.toString();
        assertNotNull(str);
        assertTrue(str.contains("CreateChatRequest"));
    }

    @Test
    void testAddParticipantsRequestToStringWithNullFields() {
        ChatDto.AddParticipantsRequest dto = new ChatDto.AddParticipantsRequest(null, null);
        String str = dto.toString();
        assertNotNull(str);
        assertTrue(str.contains("AddParticipantsRequest"));
    }

    @Test
    void testCreatePrivateChatRequestToStringWithNullFields() {
        ChatDto.CreatePrivateChatRequest dto = new ChatDto.CreatePrivateChatRequest(null);
        String str = dto.toString();
        assertNotNull(str);
        assertTrue(str.contains("CreatePrivateChatRequest"));
    }

    @Test
    void testChatDtoEqualsAndHashCodeWithNullFields() {
        ChatDto dto1 = new ChatDto(null, null, null, null, null, null, null, null, null, null, null);
        ChatDto dto2 = new ChatDto(null, null, null, null, null, null, null, null, null, null, null);
        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void testChatDtoEqualsWithDifferentObjects() {
        ChatDto dto1 = new ChatDto(1L, "chat1", null, "desc1", "avatar1", null, null, null, java.util.Collections.emptyList(), null, 1);
        ChatDto dto2 = new ChatDto(2L, "chat2", null, "desc2", "avatar2", null, null, null, java.util.Collections.emptyList(), null, 2);
        assertNotEquals(dto1, dto2);
        assertNotEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void testChatDtoToStringWithNullFields() {
        ChatDto dto = new ChatDto(null, null, null, null, null, null, null, null, null, null, null);
        String str = dto.toString();
        assertNotNull(str);
        assertTrue(str.contains("ChatDto"));
    }

    @Test
    void testChatDtoWithEmptyParticipants() {
        ChatDto dto = new ChatDto(1L, "chat", null, "desc", "avatar", LocalDateTime.now(), LocalDateTime.now(), null, java.util.Collections.emptyList(), null, 0);
        assertEquals(0, dto.getUnreadCount());
        assertEquals(java.util.Collections.emptyList(), dto.getParticipants());
    }

    @Test
    void testEqualsWithNull() {
        ChatDto dto = new ChatDto();
        assertNotEquals(dto, null);
    }

    @Test
    void testEqualsWithOtherClass() {
        ChatDto dto = new ChatDto();
        assertNotEquals(dto, "string");
    }

    @Test
    void testEqualsWithSelf() {
        ChatDto dto = new ChatDto();
        assertEquals(dto, dto);
    }

    @Test
    void testEqualsWithDifferentFields() {
        ChatDto dto1 = new ChatDto();
        ChatDto dto2 = new ChatDto();
        dto1.setId(1L);
        dto2.setId(2L);
        assertNotEquals(dto1, dto2);
    }

    @Test
    void testEqualsWithSameFields() {
        ChatDto dto1 = new ChatDto();
        ChatDto dto2 = new ChatDto();
        dto1.setId(1L);
        dto2.setId(1L);
        dto1.setChatName("abc");
        dto2.setChatName("abc");
        dto1.setUnreadCount(5);
        dto2.setUnreadCount(5);
        assertEquals(dto1, dto2);
    }

    @Test
    void testEqualsWithDifferentChatName() {
        ChatDto dto1 = new ChatDto(1L, "chat1", null, "desc", "avatar", LocalDateTime.now(), LocalDateTime.now(), null, java.util.Collections.emptyList(), null, 3);
        ChatDto dto2 = new ChatDto(1L, "chat2", null, "desc", "avatar", dto1.getCreatedAt(), dto1.getLastMessageAt(), null, java.util.Collections.emptyList(), null, 3);
        assertNotEquals(dto1, dto2);
    }

    @Test
    void testEqualsWithDifferentChatType() {
        ChatDto dto1 = new ChatDto(1L, "chat", Chat.ChatType.GROUP, "desc", "avatar", LocalDateTime.now(), LocalDateTime.now(), null, java.util.Collections.emptyList(), null, 3);
        ChatDto dto2 = new ChatDto(1L, "chat", Chat.ChatType.PRIVATE, "desc", "avatar", dto1.getCreatedAt(), dto1.getLastMessageAt(), null, java.util.Collections.emptyList(), null, 3);
        assertNotEquals(dto1, dto2);
    }

    @Test
    void testEqualsWithDifferentParticipants() {
        UserDto u1 = new UserDto(1L, "user1", null, null, null, null, null, null, null, null);
        UserDto u2 = new UserDto(2L, "user2", null, null, null, null, null, null, null, null);
        ChatDto dto1 = new ChatDto(1L, "chat", Chat.ChatType.GROUP, "desc", "avatar", LocalDateTime.now(), LocalDateTime.now(), null, java.util.Arrays.asList(u1), null, 3);
        ChatDto dto2 = new ChatDto(1L, "chat", Chat.ChatType.GROUP, "desc", "avatar", dto1.getCreatedAt(), dto1.getLastMessageAt(), null, java.util.Arrays.asList(u2), null, 3);
        assertNotEquals(dto1, dto2);
    }

    @Test
    void testEqualsWithDifferentLastMessage() {
        MessageDto m1 = new MessageDto(); m1.setId(1L);
        MessageDto m2 = new MessageDto(); m2.setId(2L);
        ChatDto dto1 = new ChatDto(1L, "chat", Chat.ChatType.GROUP, "desc", "avatar", LocalDateTime.now(), LocalDateTime.now(), null, java.util.Collections.emptyList(), m1, 3);
        ChatDto dto2 = new ChatDto(1L, "chat", Chat.ChatType.GROUP, "desc", "avatar", dto1.getCreatedAt(), dto1.getLastMessageAt(), null, java.util.Collections.emptyList(), m2, 3);
        assertNotEquals(dto1, dto2);
    }

    @Test
    void testEqualsWithAllFieldsSame() {
        UserDto u = new UserDto(1L, "user", null, null, null, null, null, null, null, null);
        MessageDto m = new MessageDto(); m.setId(1L);
        LocalDateTime dt = LocalDateTime.of(2023,1,1,0,0);
        ChatDto dto1 = new ChatDto(1L, "chat", Chat.ChatType.GROUP, "desc", "avatar", dt, dt, null, java.util.Arrays.asList(u), m, 3);
        ChatDto dto2 = new ChatDto(1L, "chat", Chat.ChatType.GROUP, "desc", "avatar", dt, dt, null, java.util.Arrays.asList(u), m, 3);
        assertEquals(dto1, dto2);
    }

    @Test
    void testEqualsWithNullFields() {
        ChatDto dto1 = new ChatDto(null, null, null, null, null, null, null, null, null, null, null);
        ChatDto dto2 = new ChatDto(null, null, null, null, null, null, null, null, null, null, null);
        assertEquals(dto1, dto2);
    }

    @Test
    void testEqualsWithSameIdDifferentFields() {
        ChatDto dto1 = new ChatDto(1L, "chat", null, "desc", "avatar", null, null, null, null, null, null);
        ChatDto dto2 = new ChatDto(1L, "chat2", null, "desc", "avatar", null, null, null, null, null, null);
        assertNotEquals(dto1, dto2);
    }
}
