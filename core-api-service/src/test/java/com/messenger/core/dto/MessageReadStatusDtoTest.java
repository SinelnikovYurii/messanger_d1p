package com.messenger.core.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;

class MessageReadStatusDtoTest {
    @Test
    void testGettersAndSetters() {
        MessageReadStatusDto dto = new MessageReadStatusDto();
        dto.setId(1L);
        dto.setMessageId(2L);
        UserDto user = new UserDto();
        dto.setUser(user);
        LocalDateTime now = LocalDateTime.now();
        dto.setReadAt(now);
        assertEquals(1L, dto.getId());
        assertEquals(2L, dto.getMessageId());
        assertEquals(user, dto.getUser());
        assertEquals(now, dto.getReadAt());
    }

    @Test
    void testEqualsAndHashCode() {
        UserDto user = new UserDto();
        MessageReadStatusDto dto1 = new MessageReadStatusDto(1L, 2L, user, LocalDateTime.now());
        MessageReadStatusDto dto2 = new MessageReadStatusDto(1L, 2L, user, dto1.getReadAt());
        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void testToString() {
        UserDto user = new UserDto();
        MessageReadStatusDto dto = new MessageReadStatusDto(1L, 2L, user, LocalDateTime.now());
        String str = dto.toString();
        assertTrue(str.contains("1"));
        assertTrue(str.contains("2"));
    }

    @Test
    void testNullArguments() {
        MessageReadStatusDto dto = new MessageReadStatusDto(null, null, null, null);
        assertNull(dto.getId());
        assertNull(dto.getMessageId());
        assertNull(dto.getUser());
        assertNull(dto.getReadAt());
    }

    @Test
    void testEqualsWithDifferentObjects() {
        UserDto user1 = new UserDto();
        UserDto user2 = new UserDto();
        MessageReadStatusDto dto1 = new MessageReadStatusDto(1L, 2L, user1, LocalDateTime.now());
        MessageReadStatusDto dto2 = new MessageReadStatusDto(2L, 3L, user2, LocalDateTime.now());
        assertNotEquals(dto1, dto2);
        assertNotEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void testEqualsWithDifferentMessageId() {
        UserDto user = new UserDto();
        MessageReadStatusDto dto1 = new MessageReadStatusDto(1L, 2L, user, LocalDateTime.now());
        MessageReadStatusDto dto2 = new MessageReadStatusDto(1L, 3L, user, dto1.getReadAt());
        assertNotEquals(dto1, dto2);
    }

    @Test
    void testEqualsWithDifferentUser() {
        UserDto user1 = new UserDto(); user1.setId(10L);
        UserDto user2 = new UserDto(); user2.setId(11L);
        MessageReadStatusDto dto1 = new MessageReadStatusDto(1L, 2L, user1, LocalDateTime.now());
        MessageReadStatusDto dto2 = new MessageReadStatusDto(1L, 2L, user2, dto1.getReadAt());
        assertNotEquals(dto1, dto2);
    }

    @Test
    void testEqualsWithDifferentReadAt() {
        UserDto user = new UserDto();
        MessageReadStatusDto dto1 = new MessageReadStatusDto(1L, 2L, user, LocalDateTime.of(2023,1,1,0,0));
        MessageReadStatusDto dto2 = new MessageReadStatusDto(1L, 2L, user, LocalDateTime.of(2024,1,1,0,0));
        assertNotEquals(dto1, dto2);
    }

    @Test
    void testEqualsWithAllFieldsSame() {
        UserDto user = new UserDto();
        LocalDateTime dt = LocalDateTime.of(2023,1,1,0,0);
        MessageReadStatusDto dto1 = new MessageReadStatusDto(1L, 2L, user, dt);
        MessageReadStatusDto dto2 = new MessageReadStatusDto(1L, 2L, user, dt);
        assertEquals(dto1, dto2);
    }

    @Test
    void testEqualsWithNullFields() {
        MessageReadStatusDto dto1 = new MessageReadStatusDto(null, null, null, null);
        MessageReadStatusDto dto2 = new MessageReadStatusDto(null, null, null, null);
        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void testEqualsWithNull() {
        MessageReadStatusDto dto = new MessageReadStatusDto();
        assertNotEquals(dto, null);
    }

    @Test
    void testEqualsWithOtherClass() {
        MessageReadStatusDto dto = new MessageReadStatusDto();
        assertNotEquals(dto, "string");
    }

    @Test
    void testEqualsWithSelf() {
        MessageReadStatusDto dto = new MessageReadStatusDto();
        assertEquals(dto, dto);
    }

    @Test
    void testEqualsWithDifferentFields() {
        MessageReadStatusDto dto1 = new MessageReadStatusDto();
        MessageReadStatusDto dto2 = new MessageReadStatusDto();
        dto1.setId(1L);
        dto2.setId(2L);
        assertNotEquals(dto1, dto2);
    }

    @Test
    void testEqualsWithSameFields() {
        MessageReadStatusDto dto1 = new MessageReadStatusDto();
        MessageReadStatusDto dto2 = new MessageReadStatusDto();
        dto1.setId(1L);
        dto2.setId(1L);
        dto1.setMessageId(2L);
        dto2.setMessageId(2L);
        assertEquals(dto1, dto2);
    }

    @Test
    void testEqualsWithSameIdDifferentFields() {
        UserDto user = new UserDto();
        MessageReadStatusDto dto1 = new MessageReadStatusDto(1L, 2L, user, null);
        MessageReadStatusDto dto2 = new MessageReadStatusDto(1L, 2L, null, null);
        assertNotEquals(dto1, dto2);
    }
}
