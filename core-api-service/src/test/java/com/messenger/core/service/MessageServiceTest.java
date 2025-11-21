package com.messenger.core.service;

import com.messenger.core.dto.MessageDto;
import com.messenger.core.model.Message;
import com.messenger.core.model.Chat;
import com.messenger.core.model.User;
import com.messenger.core.model.MessageReadStatus;
import com.messenger.core.dto.MessageReadStatusDto;
import com.messenger.core.repository.MessageRepository;
import com.messenger.core.repository.ChatRepository;
import com.messenger.core.repository.UserRepository;
import com.messenger.core.repository.MessageReadStatusRepository;
import org.springframework.kafka.core.KafkaTemplate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Collections;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

class MessageServiceTest {
    @Mock private MessageRepository messageRepository;
    @Mock private ChatRepository chatRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private MessageReadStatusRepository messageReadStatusRepository;
    @InjectMocks private MessageService messageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSendMessage() {
        User user = new User(); user.setId(1L);
        Chat chat = new Chat(); chat.setId(5L);
        chat.setParticipants(new HashSet<>(List.of(user)));
        Message message = new Message();
        message.setId(10L);
        message.setContent("Hello!");
        message.setSender(user);
        message.setChat(chat);
        MessageDto.SendMessageRequest request = new MessageDto.SendMessageRequest();
        request.setChatId(5L);
        request.setContent("Hello!");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatRepository.findById(5L)).thenReturn(Optional.of(chat));
        when(messageRepository.save(any(Message.class))).thenReturn(message);
        MessageDto result = messageService.sendMessage(1L, request);
        assertNotNull(result);
        assertEquals("Hello!", result.getContent());
    }

    @Test
    void testGetMessagesByChatId() {
        Chat chat = new Chat(); chat.setId(5L);
        User user = new User(); user.setId(1L);
        chat.setParticipants(new HashSet<>(List.of(user)));
        Message msg1 = new Message(); msg1.setId(1L); msg1.setContent("msg1"); msg1.setChat(chat);
        Message msg2 = new Message(); msg2.setId(2L); msg2.setContent("msg2"); msg2.setChat(chat);
        when(chatRepository.isUserParticipant(5L, 1L)).thenReturn(true);
        when(chatRepository.existsById(5L)).thenReturn(true);
        when(messageRepository.findByChatIdOrderByCreatedAtDescWithSender(eq(5L), any())).thenReturn(List.of(msg1, msg2));
        when(messageReadStatusRepository.findByMessageIdIn(anyList())).thenReturn(Collections.emptyList());
        List<MessageDto> messages = messageService.getChatMessages(5L, 1L, 0, 50);
        assertEquals(2, messages.size());
        assertEquals("msg1", messages.get(0).getContent());
        assertEquals("msg2", messages.get(1).getContent());
    }

    @Test
    void testEditMessage() {
        User sender = new User(); sender.setId(2L);
        Chat chat = new Chat(); chat.setId(5L);
        Message message = new Message();
        message.setId(1L);
        message.setSender(sender);
        message.setIsDeleted(false);
        message.setChat(chat);
        MessageDto.EditMessageRequest req = new MessageDto.EditMessageRequest();
        req.setContent("new content");
        when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(Message.class))).thenReturn(new Message() {{
            setId(1L);
            setSender(sender);
            setIsDeleted(false);
            setChat(chat);
            setContent("new content");
            setIsEdited(true); // Исправлено!
        }});
        MessageDto dto = messageService.editMessage(1L, 2L, req);
        assertEquals("new content", dto.getContent());
        assertTrue(dto.getIsEdited());
    }

    @Test
    void testDeleteMessage() {
        User sender = new User(); sender.setId(2L);
        Chat chat = new Chat(); chat.setId(5L);
        Message message = new Message();
        message.setId(1L);
        message.setSender(sender);
        message.setIsDeleted(false);
        message.setChat(chat);
        when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(Message.class))).thenReturn(new Message() {{
            setId(1L);
            setSender(sender);
            setIsDeleted(true);
            setChat(chat);
            setContent("[Сообщение удалено]");
        }});
        messageService.deleteMessage(1L, 2L);
        assertTrue(message.getIsDeleted());
        assertEquals("[Сообщение удалено]", message.getContent());
    }

    @Test
    void testSearchMessagesInChat() {
        Chat chat = new Chat(); chat.setId(5L);
        User user = new User(); user.setId(1L);
        chat.setParticipants(new HashSet<>(List.of(user)));
        Message msg = new Message(); msg.setId(1L); msg.setContent("search");
        msg.setChat(chat); // ВАЖНО!
        when(chatRepository.findById(5L)).thenReturn(Optional.of(chat));
        when(messageRepository.searchMessagesInChat(eq(5L), eq("search"), any())).thenReturn(Collections.singletonList(msg));
        var result = messageService.searchMessagesInChat(5L, 1L, "search", 0, 10);
        assertEquals(1, result.size());
        assertEquals("search", result.get(0).getContent());
    }

    @Test
    void testGetUnreadMessagesCount() {
        when(messageReadStatusRepository.countUnreadMessagesInChat(5L, 1L)).thenReturn(3L);
        long count = messageService.getUnreadMessagesCount(1L, 5L);
        assertEquals(3L, count);
    }

    @Test
    void testGetMessageReadStatuses() {
        Message message = new Message(); message.setId(1L);
        Chat chat = new Chat(); chat.setId(5L);
        User user = new User(); user.setId(1L);
        chat.setParticipants(new HashSet<>(List.of(user)));
        message.setChat(chat);
        when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
        MessageReadStatus status = new MessageReadStatus();
        status.setId(10L); status.setMessage(message); status.setUser(user); status.setReadAt(LocalDateTime.now());
        when(messageReadStatusRepository.findByMessageId(1L)).thenReturn(Collections.singletonList(status));
        when(userService.convertToDto(user)).thenReturn(new com.messenger.core.dto.UserDto());
        var result = messageService.getMessageReadStatuses(1L, 1L);
        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getId());
    }

    @Test
    void testMarkMessagesAsRead() {
        User user = new User(); user.setId(1L);
        User sender = new User(); sender.setId(3L);
        Chat chat = new Chat(); chat.setId(5L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(messageRepository.findAllById(anyList())).thenReturn(Collections.singletonList(new Message() {{
            setId(2L);
            setSender(sender);
            setChat(chat);
        }}));
        when(messageReadStatusRepository.existsByMessageIdAndUserId(2L, 1L)).thenReturn(false);
        messageService.markMessagesAsRead(1L, Collections.singletonList(2L));
        verify(messageReadStatusRepository, times(1)).save(any(MessageReadStatus.class));
    }

    @Test
    void testMarkAllChatMessagesAsRead() {
        Chat chat = new Chat(); chat.setId(5L);
        User user = new User(); user.setId(1L);
        chat.setParticipants(new HashSet<>(List.of(user)));
        when(chatRepository.findById(5L)).thenReturn(Optional.of(chat));
        when(messageReadStatusRepository.findUnreadMessageIdsInChat(5L, 1L)).thenReturn(Collections.singletonList(2L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        User sender = new User(); sender.setId(3L);
        Chat chat2 = new Chat(); chat2.setId(5L);
        when(messageRepository.findAllById(anyList())).thenReturn(Collections.singletonList(new Message() {{
            setId(2L);
            setSender(sender);
            setChat(chat2);
        }}));
        when(messageReadStatusRepository.existsByMessageIdAndUserId(2L, 1L)).thenReturn(false);
        messageService.markAllChatMessagesAsRead(1L, 5L);
        verify(messageReadStatusRepository, times(1)).save(any(MessageReadStatus.class));
    }

    @Test
    void testConvertToDto() {
        Message message = new Message();
        message.setId(1L);
        message.setContent("dto");
        Chat chat = new Chat(); chat.setId(5L);
        message.setChat(chat);
        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        message.setMessageType(Message.MessageType.TEXT);
        message.setIsEdited(false);
        message.setIsDeleted(false);
        User sender = new User(); sender.setId(2L);
        message.setSender(sender);
        when(userService.convertToDto(sender)).thenReturn(new com.messenger.core.dto.UserDto());
        MessageDto dto = messageService.convertToDto(message);
        assertEquals(1L, dto.getId());
        assertEquals("dto", dto.getContent());
    }

    @Test
    void testSendMessageToNonexistentChat() {
        User user = new User(); user.setId(1L);
        MessageDto.SendMessageRequest request = new MessageDto.SendMessageRequest();
        request.setChatId(999L);
        request.setContent("Hello!");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> messageService.sendMessage(1L, request));
    }

    @Test
    void testSendMessageFromNonexistentUser() {
        MessageDto.SendMessageRequest request = new MessageDto.SendMessageRequest();
        request.setChatId(5L);
        request.setContent("Hello!");
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> messageService.sendMessage(999L, request));
    }

    @Test
    void testEditMessageNoRights() {
        User sender = new User(); sender.setId(2L);
        User other = new User(); other.setId(3L);
        Chat chat = new Chat(); chat.setId(5L);
        Message message = new Message();
        message.setId(1L);
        message.setSender(sender);
        message.setIsDeleted(false);
        message.setChat(chat);
        MessageDto.EditMessageRequest req = new MessageDto.EditMessageRequest();
        req.setContent("new content");
        when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
        assertThrows(RuntimeException.class, () -> messageService.editMessage(1L, 3L, req));
    }

    @Test
    void testEditNonexistentMessage() {
        MessageDto.EditMessageRequest req = new MessageDto.EditMessageRequest();
        req.setContent("new content");
        when(messageRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> messageService.editMessage(999L, 1L, req));
    }

    @Test
    void testDeleteNonexistentMessage() {
        when(messageRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> messageService.deleteMessage(999L, 1L));
    }

    @Test
    void testSendEmptyMessage() {
        User user = new User(); user.setId(1L);
        Chat chat = new Chat(); chat.setId(5L);
        chat.setParticipants(new HashSet<>(List.of(user)));
        MessageDto.SendMessageRequest request = new MessageDto.SendMessageRequest();
        request.setChatId(5L);
        request.setContent("");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatRepository.findById(5L)).thenReturn(Optional.of(chat));
        assertThrows(IllegalArgumentException.class, () -> messageService.sendMessage(1L, request));
    }

    @Test
    void testSendTooLongMessage() {
        User user = new User(); user.setId(1L);
        Chat chat = new Chat(); chat.setId(5L);
        chat.setParticipants(new HashSet<>(List.of(user)));
        MessageDto.SendMessageRequest request = new MessageDto.SendMessageRequest();
        request.setChatId(5L);
        request.setContent("a".repeat(10001)); // Предположим лимит 10000
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatRepository.findById(5L)).thenReturn(Optional.of(chat));
        assertThrows(IllegalArgumentException.class, () -> messageService.sendMessage(1L, request));
    }

    @Test
    void testGetMessagesByChatIdNotParticipant() {
        Chat chat = new Chat(); chat.setId(5L);
        User user = new User(); user.setId(1L);
        chat.setParticipants(new HashSet<>()); // Пользователь не участник
        when(chatRepository.isUserParticipant(5L, 1L)).thenReturn(false);
        when(chatRepository.existsById(5L)).thenReturn(true);
        assertThrows(RuntimeException.class, () -> messageService.getChatMessages(5L, 1L, 0, 50));
    }

    @Test
    void testSendMessageWithFile() {
        User user = new User(); user.setId(1L);
        Chat chat = new Chat(); chat.setId(5L);
        chat.setParticipants(new HashSet<>(List.of(user)));
        Message message = new Message();
        message.setId(10L);
        message.setContent("File message");
        message.setSender(user);
        message.setChat(chat);
        message.setFileUrl("file.png");
        message.setFileName("file.png");
        message.setFileSize(123L);
        MessageDto.SendMessageRequest request = new MessageDto.SendMessageRequest();
        request.setChatId(5L);
        request.setContent("File message");
        request.setFileUrl("file.png");
        request.setFileName("file.png");
        request.setFileSize(123L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatRepository.findById(5L)).thenReturn(Optional.of(chat));
        when(messageRepository.save(any(Message.class))).thenReturn(message);
        MessageDto result = messageService.sendMessage(1L, request);
        assertEquals("file.png", result.getFileUrl());
        assertEquals("file.png", result.getFileName());
        assertEquals(123L, result.getFileSize());
    }

    @Test
    void testSendMessageWithReply() {
        User user = new User(); user.setId(1L);
        Chat chat = new Chat(); chat.setId(5L);
        chat.setParticipants(new HashSet<>(List.of(user)));
        Message replyMsg = new Message(); replyMsg.setId(99L);
        Message message = new Message();
        message.setId(10L);
        message.setContent("Reply message");
        message.setSender(user);
        message.setChat(chat);
        message.setReplyToMessage(replyMsg);
        MessageDto.SendMessageRequest request = new MessageDto.SendMessageRequest();
        request.setChatId(5L);
        request.setContent("Reply message");
        request.setReplyToMessageId(99L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatRepository.findById(5L)).thenReturn(Optional.of(chat));
        when(messageRepository.findById(99L)).thenReturn(Optional.of(replyMsg));
        when(messageRepository.save(any(Message.class))).thenReturn(message);
        MessageDto result = messageService.sendMessage(1L, request);
        assertNotNull(result);
    }

    @Test
    void testSendMessageWithDifferentTypes() {
        User user = new User(); user.setId(1L);
        Chat chat = new Chat(); chat.setId(5L);
        chat.setParticipants(new HashSet<>(List.of(user)));
        Message message = new Message();
        message.setId(10L);
        message.setContent("Image message");
        message.setSender(user);
        message.setChat(chat);
        message.setMessageType(Message.MessageType.IMAGE);
        MessageDto.SendMessageRequest request = new MessageDto.SendMessageRequest();
        request.setChatId(5L);
        request.setContent("Image message");
        request.setMessageType(Message.MessageType.IMAGE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatRepository.findById(5L)).thenReturn(Optional.of(chat));
        when(messageRepository.save(any(Message.class))).thenReturn(message);
        MessageDto result = messageService.sendMessage(1L, request);
        assertEquals(Message.MessageType.IMAGE, result.getMessageType());
    }

    @Test
    void testMarkMessagesAsReadAlreadyRead() {
        User user = new User(); user.setId(1L);
        User sender = new User(); sender.setId(3L);
        Chat chat = new Chat(); chat.setId(5L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(messageRepository.findAllById(anyList())).thenReturn(Collections.singletonList(new Message() {{
            setId(2L);
            setSender(sender);
            setChat(chat);
        }}));
        when(messageReadStatusRepository.existsByMessageIdAndUserId(2L, 1L)).thenReturn(true);
        messageService.markMessagesAsRead(1L, Collections.singletonList(2L));
        verify(messageReadStatusRepository, never()).save(any(MessageReadStatus.class));
    }

    @Test
    void testGetChatMessagesWithDeletedAndEditedMessages() {
        Chat chat = new Chat(); chat.setId(5L);
        User user = new User(); user.setId(1L);
        chat.setParticipants(new HashSet<>(List.of(user)));
        Message msg1 = new Message(); msg1.setId(1L); msg1.setContent("msg1"); msg1.setChat(chat); msg1.setIsDeleted(true);
        Message msg2 = new Message(); msg2.setId(2L); msg2.setContent("msg2"); msg2.setChat(chat); msg2.setIsEdited(true);
        when(chatRepository.isUserParticipant(5L, 1L)).thenReturn(true);
        when(chatRepository.existsById(5L)).thenReturn(true);
        when(messageRepository.findByChatIdOrderByCreatedAtDescWithSender(eq(5L), any())).thenReturn(List.of(msg1, msg2));
        when(messageReadStatusRepository.findByMessageIdIn(anyList())).thenReturn(Collections.emptyList());
        List<MessageDto> messages = messageService.getChatMessages(5L, 1L, 0, 50);
        assertEquals(2, messages.size());
        assertTrue(Boolean.TRUE.equals(messages.get(0).getIsDeleted()) || Boolean.TRUE.equals(messages.get(1).getIsEdited()));
    }

    @Test
    void testSearchMessagesInChatEmptyResult() {
        Chat chat = new Chat(); chat.setId(5L);
        User user = new User(); user.setId(1L);
        chat.setParticipants(new HashSet<>(List.of(user)));
        when(chatRepository.findById(5L)).thenReturn(Optional.of(chat));
        when(messageRepository.searchMessagesInChat(eq(5L), eq("notfound"), any())).thenReturn(Collections.emptyList());
        var result = messageService.searchMessagesInChat(5L, 1L, "notfound", 0, 10);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetMessageReadStatusesNonexistentMessage() {
        when(messageRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> messageService.getMessageReadStatuses(999L, 1L));
    }

    @Test
    void testSendMessageToChatWithoutParticipants() {
        User user = new User(); user.setId(1L);
        Chat chat = new Chat(); chat.setId(5L);
        chat.setParticipants(new HashSet<>());
        MessageDto.SendMessageRequest request = new MessageDto.SendMessageRequest();
        request.setChatId(5L);
        request.setContent("Hello!");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatRepository.findById(5L)).thenReturn(Optional.of(chat));
        assertThrows(RuntimeException.class, () -> messageService.sendMessage(1L, request));
    }

    @Test
    void testSendMessageFromUserNotInChat() {
        User user = new User(); user.setId(1L);
        Chat chat = new Chat(); chat.setId(5L);
        chat.setParticipants(new HashSet<>());
        MessageDto.SendMessageRequest request = new MessageDto.SendMessageRequest();
        request.setChatId(5L);
        request.setContent("Hello!");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatRepository.findById(5L)).thenReturn(Optional.of(chat));
        assertThrows(RuntimeException.class, () -> messageService.sendMessage(1L, request));
    }

    @Test
    void testConvertToDtoWithAllFields() {
        Message message = new Message();
        message.setId(1L);
        message.setContent("test content");
        message.setMessageType(Message.MessageType.TEXT);
        message.setIsEdited(true);
        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        message.setFileUrl("file.png");
        message.setFileName("file.png");
        message.setFileSize(123L);
        message.setMimeType("image/png");
        message.setThumbnailUrl("thumb.png");
        message.setIsDeleted(false);
        Chat chat = new Chat(); chat.setId(5L);
        message.setChat(chat);
        User sender = new User(); sender.setId(2L);
        message.setSender(sender);
        Message reply = new Message(); reply.setId(99L); reply.setChat(chat);
        message.setReplyToMessage(reply);
        when(userService.convertToDto(sender)).thenReturn(new com.messenger.core.dto.UserDto());
        when(messageReadStatusRepository.existsByMessageIdAndUserId(1L, 2L)).thenReturn(true);
        when(messageReadStatusRepository.findByMessageIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        when(messageReadStatusRepository.countByMessageId(1L)).thenReturn(5L);
        MessageDto dto = messageService.convertToDto(message, 2L, true);
        assertEquals(1L, dto.getId());
        assertEquals("test content", dto.getContent());
        assertEquals(Message.MessageType.TEXT, dto.getMessageType());
        assertTrue(dto.getIsEdited());
        assertEquals("file.png", dto.getFileUrl());
        assertEquals("file.png", dto.getFileName());
        assertEquals(123L, dto.getFileSize());
        assertEquals("image/png", dto.getMimeType());
        assertEquals("thumb.png", dto.getThumbnailUrl());
        assertEquals(Integer.valueOf(5), dto.getReadCount());
        assertTrue(dto.getIsReadByCurrentUser());
        assertNotNull(dto.getReplyToMessage());
        assertEquals(5L, dto.getChatId());
        assertFalse(Boolean.TRUE.equals(dto.getIsDeleted()));
    }

    @Test
    void testConvertToDtoWithNullFields() {
        Message message = new Message();
        message.setId(2L);
        message.setContent(null);
        message.setMessageType(null);
        message.setIsEdited(null);
        message.setCreatedAt(null);
        message.setUpdatedAt(null);
        message.setFileUrl(null);
        message.setFileName(null);
        message.setFileSize(null);
        message.setMimeType(null);
        message.setThumbnailUrl(null);
        message.setIsDeleted(null);
        message.setChat(null);
        message.setSender(null);
        message.setReplyToMessage(null);
        when(messageReadStatusRepository.existsByMessageIdAndUserId(2L, 3L)).thenReturn(false);
        when(messageReadStatusRepository.countByMessageId(2L)).thenReturn(0L);
        MessageDto dto = messageService.convertToDto(message, 3L, false);
        assertEquals(2L, dto.getId());
        assertNull(dto.getContent());
        assertNull(dto.getMessageType());
        assertNull(dto.getIsEdited());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getUpdatedAt());
        assertNull(dto.getFileUrl());
        assertNull(dto.getFileName());
        assertNull(dto.getFileSize());
        assertNull(dto.getMimeType());
        assertNull(dto.getThumbnailUrl());
        assertNull(dto.getChatId());
        assertNull(dto.getSender());
        assertNull(dto.getReplyToMessage());
        assertEquals(0, dto.getReadCount());
        assertFalse(Boolean.TRUE.equals(dto.getIsReadByCurrentUser()));
        assertNull(dto.getIsDeleted());
    }

    @Test
    void testConvertToDtoWithPreloadedStatuses_allRead() {
        Message message = new Message();
        message.setId(3L);
        message.setContent("read");
        message.setMessageType(Message.MessageType.TEXT);
        message.setIsEdited(false);
        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        Chat chat = new Chat(); chat.setId(7L);
        message.setChat(chat);
        User sender = new User(); sender.setId(4L);
        message.setSender(sender);
        MessageReadStatus status1 = new MessageReadStatus();
        status1.setMessage(message);
        User user1 = new User(); user1.setId(10L);
        status1.setUser(user1);
        status1.setReadAt(LocalDateTime.now());
        MessageReadStatus status2 = new MessageReadStatus();
        status2.setMessage(message);
        User user2 = new User(); user2.setId(11L);
        status2.setUser(user2);
        status2.setReadAt(LocalDateTime.now());
        List<MessageReadStatus> statuses = List.of(status1, status2);
        when(userService.convertToDto(sender)).thenReturn(new com.messenger.core.dto.UserDto());
        MessageDto dto = invokeConvertToDtoWithPreloadedStatuses(message, 10L, statuses);
        assertEquals(3L, dto.getId());
        assertEquals(2, dto.getReadCount());
        assertTrue(dto.getIsReadByCurrentUser());
        assertNotNull(dto.getReadAt());
    }

    @Test
    void testConvertToDtoWithPreloadedStatuses_noneRead() {
        Message message = new Message();
        message.setId(4L);
        message.setContent("not read");
        message.setMessageType(Message.MessageType.TEXT);
        message.setIsEdited(false);
        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        Chat chat = new Chat(); chat.setId(8L);
        message.setChat(chat);
        User sender = new User(); sender.setId(5L);
        message.setSender(sender);
        List<MessageReadStatus> statuses = List.of();
        when(userService.convertToDto(sender)).thenReturn(new com.messenger.core.dto.UserDto());
        MessageDto dto = invokeConvertToDtoWithPreloadedStatuses(message, 99L, statuses);
        assertEquals(4L, dto.getId());
        assertEquals(0, dto.getReadCount());
        assertFalse(Boolean.TRUE.equals(dto.getIsReadByCurrentUser()));
        assertNull(dto.getReadAt());
    }

    // Вспомогательный метод для вызова приватного метода через reflection
    private MessageDto invokeConvertToDtoWithPreloadedStatuses(Message message, Long currentUserId, List<MessageReadStatus> statuses) {
        try {
            var method = messageService.getClass().getDeclaredMethod("convertToDtoWithPreloadedStatuses", Message.class, Long.class, List.class);
            method.setAccessible(true);
            return (MessageDto) method.invoke(messageService, message, currentUserId, statuses);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
