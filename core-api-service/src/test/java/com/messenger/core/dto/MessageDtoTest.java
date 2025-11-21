package com.messenger.core.dto;

import com.messenger.core.model.Message;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MessageDtoTest {
    @Test
    void testEqualsAndHashCode_fullEquality() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void testEqualsAndHashCode_differentId() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setId(999L);
        assertNotEquals(m1, m2);
        assertNotEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void testEqualsAndHashCode_differentContent() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setContent("other");
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_differentFileFields() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setFileUrl("other.png");
        m2.setFileName("other.png");
        m2.setFileSize(999L);
        m2.setMimeType("other/type");
        m2.setThumbnailUrl("other_thumb.png");
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_differentReadStatusFields() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setIsReadByCurrentUser(false);
        m2.setReadCount(0);
        m2.setReadAt(LocalDateTime.now().plusDays(1));
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_differentReplyToMessage() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        MessageDto reply = createFullDto(); reply.setId(123L);
        m2.setReplyToMessage(reply);
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_differentSender() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        UserDto sender = new UserDto(); sender.setId(999L);
        m2.setSender(sender);
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_differentIsDeleted() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setIsDeleted(!m1.getIsDeleted());
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_nullFields() {
        MessageDto m1 = new MessageDto();
        MessageDto m2 = new MessageDto();
        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void testEqualsAndHashCode_selfAndOtherTypes() {
        MessageDto m1 = createFullDto();
        assertEquals(m1, m1);
        assertNotEquals(m1, null);
        assertNotEquals(m1, "string");
    }

    @Test
    void testEqualsAndHashCode_differentCreatedAt() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setCreatedAt(m1.getCreatedAt().plusDays(1));
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_differentUpdatedAt() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setUpdatedAt(m1.getUpdatedAt().plusDays(1));
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_differentChatId() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setChatId(999L);
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_differentMessageType() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setMessageType(Message.MessageType.IMAGE);
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_differentIsEdited() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setIsEdited(!m1.getIsEdited());
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_differentReadBy() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        MessageReadStatusDto status = new MessageReadStatusDto(); status.setId(1L);
        m2.setReadBy(List.of(status));
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_allNullVsFull() {
        MessageDto m1 = new MessageDto();
        MessageDto m2 = createFullDto();
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_differentFileSizeNullVsValue() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setFileSize(null);
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_differentReadAtNullVsValue() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setReadAt(null);
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_replyToMessageNullVsValue() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setReplyToMessage(null);
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_senderNullVsValue() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setSender(null);
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_readByNullVsValue() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setReadBy(null);
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_readCountNullVsValue() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setReadCount(null);
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_isReadByCurrentUserNullVsValue() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setIsReadByCurrentUser(null);
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_isEditedNullVsValue() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setIsEdited(null);
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_isDeletedNullVsValue() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setIsDeleted(null);
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_fileFieldsNullVsValue() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setFileUrl(null);
        m2.setFileName(null);
        m2.setFileSize(null);
        m2.setMimeType(null);
        m2.setThumbnailUrl(null);
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_createdAtNullVsValue() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setCreatedAt(null);
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_updatedAtNullVsValue() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m2.setUpdatedAt(null);
        assertNotEquals(m1, m2);
    }

    @Test
    void testEqualsAndHashCode_readAtNullVsValue2() {
        MessageDto m1 = createFullDto();
        MessageDto m2 = createFullDto();
        m1.setReadAt(null);
        assertNotEquals(m1, m2);
    }

    @Test
    void testSystemMessageDto_equalsAndHashCode_fullEquality() {
        MessageDto.SystemMessageDto s1 = createSystemDto();
        MessageDto.SystemMessageDto s2 = createSystemDto();
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void testSystemMessageDto_differentContent() {
        MessageDto.SystemMessageDto s1 = createSystemDto();
        MessageDto.SystemMessageDto s2 = createSystemDto();
        s2.setContent("other");
        assertNotEquals(s1, s2);
    }

    @Test
    void testSystemMessageDto_differentType() {
        MessageDto.SystemMessageDto s1 = createSystemDto();
        MessageDto.SystemMessageDto s2 = createSystemDto();
        s2.setMessageType(Message.MessageType.TEXT);
        assertNotEquals(s1, s2);
    }

    @Test
    void testSystemMessageDto_differentCreatedAt() {
        MessageDto.SystemMessageDto s1 = createSystemDto();
        MessageDto.SystemMessageDto s2 = createSystemDto();
        s2.setCreatedAt(s1.getCreatedAt().plusDays(1));
        assertNotEquals(s1, s2);
    }

    @Test
    void testSystemMessageDto_nullFields() {
        MessageDto.SystemMessageDto s1 = new MessageDto.SystemMessageDto();
        MessageDto.SystemMessageDto s2 = new MessageDto.SystemMessageDto();
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void testSystemMessageDto_selfAndOtherTypes() {
        MessageDto.SystemMessageDto s1 = createSystemDto();
        assertEquals(s1, s1);
        assertNotEquals(s1, null);
        assertNotEquals(s1, "string");
    }

    @Test
    void testSystemMessageDto_contentNullVsValue() {
        MessageDto.SystemMessageDto s1 = createSystemDto();
        MessageDto.SystemMessageDto s2 = createSystemDto();
        s2.setContent(null);
        assertNotEquals(s1, s2);
    }

    @Test
    void testSystemMessageDto_typeNullVsValue() {
        MessageDto.SystemMessageDto s1 = createSystemDto();
        MessageDto.SystemMessageDto s2 = createSystemDto();
        s2.setMessageType(null);
        assertNotEquals(s1, s2);
    }

    @Test
    void testSystemMessageDto_createdAtNullVsValue() {
        MessageDto.SystemMessageDto s1 = createSystemDto();
        MessageDto.SystemMessageDto s2 = createSystemDto();
        s2.setCreatedAt(null);
        assertNotEquals(s1, s2);
    }

    @Test
    void testEditMessageRequest_equalsAndHashCode_fullEquality() {
        MessageDto.EditMessageRequest r1 = new MessageDto.EditMessageRequest(1L, "content");
        MessageDto.EditMessageRequest r2 = new MessageDto.EditMessageRequest(1L, "content");
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void testEditMessageRequest_differentMessageId() {
        MessageDto.EditMessageRequest r1 = new MessageDto.EditMessageRequest(1L, "content");
        MessageDto.EditMessageRequest r2 = new MessageDto.EditMessageRequest(2L, "content");
        assertNotEquals(r1, r2);
    }

    @Test
    void testEditMessageRequest_differentContent() {
        MessageDto.EditMessageRequest r1 = new MessageDto.EditMessageRequest(1L, "content");
        MessageDto.EditMessageRequest r2 = new MessageDto.EditMessageRequest(1L, "other");
        assertNotEquals(r1, r2);
    }

    @Test
    void testEditMessageRequest_nullFields() {
        MessageDto.EditMessageRequest r1 = new MessageDto.EditMessageRequest();
        MessageDto.EditMessageRequest r2 = new MessageDto.EditMessageRequest();
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void testEditMessageRequest_selfAndOtherTypes() {
        MessageDto.EditMessageRequest r1 = new MessageDto.EditMessageRequest(1L, "content");
        assertEquals(r1, r1);
        assertNotEquals(r1, null);
        assertNotEquals(r1, "string");
    }

    @Test
    void testEditMessageRequest_messageIdNullVsValue() {
        MessageDto.EditMessageRequest r1 = new MessageDto.EditMessageRequest(1L, "content");
        MessageDto.EditMessageRequest r2 = new MessageDto.EditMessageRequest(null, "content");
        assertNotEquals(r1, r2);
    }

    @Test
    void testEditMessageRequest_contentNullVsValue() {
        MessageDto.EditMessageRequest r1 = new MessageDto.EditMessageRequest(1L, "content");
        MessageDto.EditMessageRequest r2 = new MessageDto.EditMessageRequest(1L, null);
        assertNotEquals(r1, r2);
    }

    @Test
    void testSendMessageRequest_equalsAndHashCode_fullEquality() {
        MessageDto.SendMessageRequest r1 = createSendRequest();
        MessageDto.SendMessageRequest r2 = createSendRequest();
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void testSendMessageRequest_differentChatId() {
        MessageDto.SendMessageRequest r1 = createSendRequest();
        MessageDto.SendMessageRequest r2 = createSendRequest();
        r2.setChatId(999L);
        assertNotEquals(r1, r2);
    }

    @Test
    void testSendMessageRequest_differentContent() {
        MessageDto.SendMessageRequest r1 = createSendRequest();
        MessageDto.SendMessageRequest r2 = createSendRequest();
        r2.setContent("other");
        assertNotEquals(r1, r2);
    }

    @Test
    void testSendMessageRequest_differentMessageType() {
        MessageDto.SendMessageRequest r1 = createSendRequest();
        MessageDto.SendMessageRequest r2 = createSendRequest();
        r2.setMessageType(Message.MessageType.IMAGE);
        assertNotEquals(r1, r2);
    }

    @Test
    void testSendMessageRequest_differentReplyToMessageId() {
        MessageDto.SendMessageRequest r1 = createSendRequest();
        MessageDto.SendMessageRequest r2 = createSendRequest();
        r2.setReplyToMessageId(123L);
        assertNotEquals(r1, r2);
    }

    @Test
    void testSendMessageRequest_differentFileFields() {
        MessageDto.SendMessageRequest r1 = createSendRequest();
        MessageDto.SendMessageRequest r2 = createSendRequest();
        r2.setFileUrl("other.png");
        r2.setFileName("other.png");
        r2.setFileSize(999L);
        r2.setMimeType("other/type");
        r2.setThumbnailUrl("other_thumb.png");
        assertNotEquals(r1, r2);
    }

    @Test
    void testSendMessageRequest_nullFields() {
        MessageDto.SendMessageRequest r1 = new MessageDto.SendMessageRequest();
        MessageDto.SendMessageRequest r2 = new MessageDto.SendMessageRequest();
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void testSendMessageRequest_selfAndOtherTypes() {
        MessageDto.SendMessageRequest r1 = createSendRequest();
        assertEquals(r1, r1);
        assertNotEquals(r1, null);
        assertNotEquals(r1, "string");
    }

    @Test
    void testSendMessageRequest_chatIdNullVsValue() {
        MessageDto.SendMessageRequest r1 = createSendRequest();
        MessageDto.SendMessageRequest r2 = createSendRequest();
        r2.setChatId(null);
        assertNotEquals(r1, r2);
    }

    @Test
    void testSendMessageRequest_contentNullVsValue() {
        MessageDto.SendMessageRequest r1 = createSendRequest();
        MessageDto.SendMessageRequest r2 = createSendRequest();
        r2.setContent(null);
        assertNotEquals(r1, r2);
    }

    @Test
    void testSendMessageRequest_messageTypeNullVsValue() {
        MessageDto.SendMessageRequest r1 = createSendRequest();
        MessageDto.SendMessageRequest r2 = createSendRequest();
        r2.setMessageType(null);
        assertNotEquals(r1, r2);
    }

    @Test
    void testSendMessageRequest_replyToMessageIdNullVsValue() {
        MessageDto.SendMessageRequest r1 = createSendRequest();
        MessageDto.SendMessageRequest r2 = createSendRequest();
        r2.setReplyToMessageId(null);
        assertNotEquals(r1, r2);
    }

    @Test
    void testSendMessageRequest_fileFieldsNullVsValue() {
        MessageDto.SendMessageRequest r1 = createSendRequest();
        MessageDto.SendMessageRequest r2 = createSendRequest();
        r2.setFileUrl(null);
        r2.setFileName(null);
        r2.setFileSize(null);
        r2.setMimeType(null);
        r2.setThumbnailUrl(null);
        assertNotEquals(r1, r2);
    }

    private MessageDto.SystemMessageDto createSystemDto() {
        return new MessageDto.SystemMessageDto("sys", Message.MessageType.SYSTEM, LocalDateTime.of(2023, 1, 1, 12, 0));
    }

    private MessageDto createFullDto() {
        MessageDto dto = new MessageDto();
        dto.setId(1L);
        dto.setContent("test");
        dto.setMessageType(Message.MessageType.TEXT);
        dto.setIsEdited(true);
        dto.setCreatedAt(LocalDateTime.of(2023, 1, 1, 12, 0));
        dto.setUpdatedAt(LocalDateTime.of(2023, 1, 2, 12, 0));
        UserDto sender = new UserDto(); sender.setId(1L);
        dto.setSender(sender);
        dto.setChatId(2L);
        MessageDto reply = new MessageDto(); reply.setId(2L);
        dto.setReplyToMessage(reply);
        dto.setFileUrl("file.png");
        dto.setFileName("file.png");
        dto.setFileSize(123L);
        dto.setMimeType("image/png");
        dto.setThumbnailUrl("thumb.png");
        dto.setIsReadByCurrentUser(true);
        dto.setReadCount(5);
        dto.setReadBy(List.of());
        dto.setReadAt(LocalDateTime.of(2023, 1, 3, 12, 0));
        dto.setIsDeleted(false);
        return dto;
    }

    private MessageDto.SendMessageRequest createSendRequest() {
        MessageDto.SendMessageRequest req = new MessageDto.SendMessageRequest();
        req.setChatId(1L);
        req.setContent("test");
        req.setMessageType(Message.MessageType.TEXT);
        req.setReplyToMessageId(2L);
        req.setFileUrl("file.png");
        req.setFileName("file.png");
        req.setFileSize(123L);
        req.setMimeType("image/png");
        req.setThumbnailUrl("thumb.png");
        return req;
    }
}
