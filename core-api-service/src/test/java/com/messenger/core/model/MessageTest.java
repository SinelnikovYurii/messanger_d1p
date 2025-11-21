package com.messenger.core.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class MessageTest {
    private Message createFullMessage(Long id, String content, Message.MessageType type, Boolean isEdited, Boolean isDeleted, String fileUrl, String fileName, Long fileSize, String mimeType, String thumbnailUrl, LocalDateTime createdAt, LocalDateTime updatedAt) {
        Message m = new Message();
        m.setId(id);
        m.setContent(content);
        m.setMessageType(type);
        m.setIsEdited(isEdited);
        m.setIsDeleted(isDeleted);
        m.setFileUrl(fileUrl);
        m.setFileName(fileName);
        m.setFileSize(fileSize);
        m.setMimeType(mimeType);
        m.setThumbnailUrl(thumbnailUrl);
        m.setCreatedAt(createdAt);
        m.setUpdatedAt(updatedAt);
        return m;
    }

    @Test
    void testEqualsAndHashCode_sameValues() {
        LocalDateTime now = LocalDateTime.now();
        Message m1 = createFullMessage(1L, "content", Message.MessageType.TEXT, true, false, "url", "name", 123L, "mime", "thumb", now, now);
        Message m2 = createFullMessage(1L, "content", Message.MessageType.TEXT, true, false, "url", "name", 123L, "mime", "thumb", now, now);
        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void testEqualsAndHashCode_differentValues() {
        LocalDateTime now = LocalDateTime.now();
        Message m1 = createFullMessage(1L, "content1", Message.MessageType.TEXT, true, false, "url1", "name1", 123L, "mime1", "thumb1", now, now);
        Message m2 = createFullMessage(2L, "content2", Message.MessageType.IMAGE, false, true, "url2", "name2", 456L, "mime2", "thumb2", now.plusDays(1), now.plusDays(1));
        assertNotEquals(m1, m2);
        assertNotEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void testEqualsAndHashCode_partialDifference() {
        LocalDateTime now = LocalDateTime.now();
        Message m1 = createFullMessage(1L, "content", Message.MessageType.TEXT, true, false, "url", "name", 123L, "mime", "thumb", now, now);
        Message m2 = createFullMessage(1L, "content", Message.MessageType.TEXT, true, false, "url", "name", 123L, "mime", "thumb2", now, now);
        assertNotEquals(m1, m2);
        assertNotEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void testEqualsAndHashCode_nullFields() {
        Message m1 = createFullMessage(null, null, null, null, null, null, null, null, null, null, null, null);
        Message m2 = createFullMessage(null, null, null, null, null, null, null, null, null, null, null, null);
        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void testEqualsAndHashCode_mixedNullFields() {
        LocalDateTime now = LocalDateTime.now();
        Message m1 = createFullMessage(1L, null, null, null, null, null, null, null, null, null, now, now);
        Message m2 = createFullMessage(1L, null, null, null, null, null, null, null, null, null, now, now);
        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
        Message m3 = createFullMessage(null, null, null, null, null, null, null, null, null, null, null, null);
        assertNotEquals(m1, m3);
    }

    @Test
    void testEquals_nullAndOtherType() {
        Message m = createFullMessage(1L, "content", Message.MessageType.TEXT, true, false, "url", "name", 123L, "mime", "thumb", LocalDateTime.now(), LocalDateTime.now());
        assertNotEquals(null, m);
        assertNotEquals("string", m);
    }

    @Test
    void testEqualsAndHashCode_emptyFields() {
        Message m1 = new Message();
        Message m2 = new Message();
        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
    }
}

