package com.messenger.core.service;

import com.messenger.core.dto.ChatDto;
import com.messenger.core.dto.MessageDto;
import com.messenger.core.model.Chat;
import com.messenger.core.model.Message;
import com.messenger.core.model.User;
import com.messenger.core.repository.ChatRepository;
import com.messenger.core.repository.MessageReadStatusRepository;
import com.messenger.core.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OptimizedDataServiceTest {
    private ChatRepository chatRepository;
    private MessageRepository messageRepository;
    private MessageReadStatusRepository messageReadStatusRepository;
    private UserService userService;
    private MessageService messageService;
    private OptimizedDataService service;

    @BeforeEach
    void setUp() {
        chatRepository = mock(ChatRepository.class);
        messageRepository = mock(MessageRepository.class);
        messageReadStatusRepository = mock(MessageReadStatusRepository.class);
        userService = mock(UserService.class);
        messageService = mock(MessageService.class);
        service = new OptimizedDataService(chatRepository, messageRepository, messageReadStatusRepository, userService, messageService);
    }

    @Test
    void testConvertChatToDtoOptimized_groupChat() throws Exception {
        Chat chat = new Chat();
        chat.setId(1L);
        chat.setChatType(Chat.ChatType.GROUP);
        chat.setChatName("Group");
        chat.setChatAvatarUrl("group.png");
        chat.setCreatedAt(LocalDateTime.now());
        chat.setLastMessageAt(LocalDateTime.now());
        chat.setCreatedBy(new User());
        chat.setParticipants(new HashSet<>(Arrays.asList(new User(), new User())));
        Message lastMessage = new Message();
        when(userService.convertToDto(any(User.class))).thenReturn(mock(com.messenger.core.dto.UserDto.class));
        when(messageService.convertToDto(any(Message.class))).thenReturn(mock(MessageDto.class));
        java.lang.reflect.Method method = service.getClass().getDeclaredMethod("convertChatToDtoOptimized", Chat.class, Message.class, Long.class, Integer.class);
        method.setAccessible(true);
        ChatDto dto = (ChatDto) method.invoke(service, chat, lastMessage, 5L, 3);
        assertNotNull(dto);
        assertEquals(chat.getId(), dto.getId());
        assertEquals(chat.getChatAvatarUrl(), dto.getChatAvatarUrl());
        assertEquals(3, dto.getUnreadCount());
    }

    @Test
    void testConvertChatToDtoOptimized_privateChat_withAvatar() throws Exception {
        Chat chat = new Chat();
        chat.setId(2L);
        chat.setChatType(Chat.ChatType.PRIVATE);
        chat.setChatName("Private");
        chat.setChatAvatarUrl("private.png");
        chat.setCreatedAt(LocalDateTime.now());
        chat.setLastMessageAt(LocalDateTime.now());
        User user1 = new User(); user1.setId(5L);
        User user2 = new User(); user2.setId(6L); user2.setProfilePictureUrl("avatar.png");
        chat.setParticipants(new HashSet<>(Arrays.asList(user1, user2)));
        chat.setCreatedBy(user1);
        Message lastMessage = new Message();
        when(userService.convertToDto(any(User.class))).thenReturn(mock(com.messenger.core.dto.UserDto.class));
        when(messageService.convertToDto(any(Message.class))).thenReturn(mock(MessageDto.class));
        java.lang.reflect.Method method = service.getClass().getDeclaredMethod("convertChatToDtoOptimized", Chat.class, Message.class, Long.class, Integer.class);
        method.setAccessible(true);
        ChatDto dto = (ChatDto) method.invoke(service, chat, lastMessage, 5L, 2);
        assertEquals("avatar.png", dto.getChatAvatarUrl());
        assertEquals(2, dto.getUnreadCount());
    }

    @Test
    void testConvertChatToDtoOptimized_privateChat_noOtherAvatar() throws Exception {
        Chat chat = new Chat();
        chat.setId(3L);
        chat.setChatType(Chat.ChatType.PRIVATE);
        chat.setChatName("Private");
        chat.setChatAvatarUrl("private.png");
        User user1 = new User(); user1.setId(5L);
        User user2 = new User(); user2.setId(6L);
        chat.setParticipants(new HashSet<>(Arrays.asList(user1, user2)));
        chat.setCreatedBy(user1);
        Message lastMessage = new Message();
        when(userService.convertToDto(any(User.class))).thenReturn(mock(com.messenger.core.dto.UserDto.class));
        when(messageService.convertToDto(any(Message.class))).thenReturn(mock(MessageDto.class));
        java.lang.reflect.Method method = service.getClass().getDeclaredMethod("convertChatToDtoOptimized", Chat.class, Message.class, Long.class, Integer.class);
        method.setAccessible(true);
        ChatDto dto = (ChatDto) method.invoke(service, chat, lastMessage, 5L, 1);
        assertEquals("private.png", dto.getChatAvatarUrl());
        assertEquals(1, dto.getUnreadCount());
    }

    @Test
    void testGetChatPerformanceStats_fullStats() {
        Chat chat = new Chat();
        chat.setId(10L);
        chat.setChatType(Chat.ChatType.GROUP);
        chat.setCreatedAt(LocalDateTime.now());
        chat.setLastMessageAt(LocalDateTime.now());
        User u1 = new User(); u1.setId(1L);
        User u2 = new User(); u2.setId(2L);
        chat.setParticipants(new HashSet<>(Arrays.asList(u1, u2)));
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(messageRepository.countMessagesByChatId(10L)).thenReturn(42L);
        Map<String, Object> stats = service.getChatPerformanceStats(10L);
        assertEquals(42L, stats.get("messageCount"));
        assertEquals(2, stats.get("participantCount"));
        assertEquals(Chat.ChatType.GROUP, stats.get("chatType"));
        assertNotNull(stats.get("createdAt"));
        assertNotNull(stats.get("lastMessageAt"));
    }

    @Test
    void testGetChatPerformanceStats_chatNotFound() {
        when(chatRepository.findById(99L)).thenReturn(Optional.empty());
        when(messageRepository.countMessagesByChatId(99L)).thenReturn(0L);
        Map<String, Object> stats = service.getChatPerformanceStats(99L);
        assertEquals(0L, stats.get("messageCount"));
        assertNull(stats.get("participantCount"));
        assertNull(stats.get("chatType"));
        assertNull(stats.get("createdAt"));
        assertNull(stats.get("lastMessageAt"));
    }

    @Test
    void testGetOptimizedChatMessages_success() {
        Long chatId = 1L, userId = 2L;
        int page = 0, size = 2;
        Message msg1 = new Message();
        Message msg2 = new Message();
        List<Message> messages = Arrays.asList(msg1, msg2);
        when(chatRepository.isUserParticipant(chatId, userId)).thenReturn(true);
        when(messageRepository.findByChatIdOrderByCreatedAtDescWithSender(eq(chatId), any(Pageable.class))).thenReturn(messages);
        when(messageService.convertToDto(any(Message.class), eq(userId), eq(false))).thenReturn(mock(MessageDto.class));
        List<MessageDto> result = service.getOptimizedChatMessages(chatId, userId, page, size);
        assertEquals(2, result.size());
    }

    @Test
    void testGetOptimizedChatMessages_accessDenied() {
        Long chatId = 1L, userId = 2L;
        when(chatRepository.isUserParticipant(chatId, userId)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.getOptimizedChatMessages(chatId, userId, 0, 10));
    }

    @Test
    void testGetOptimizedChatMessages_emptyMessages() {
        Long chatId = 1L, userId = 2L;
        when(chatRepository.isUserParticipant(chatId, userId)).thenReturn(true);
        when(messageRepository.findByChatIdOrderByCreatedAtDescWithSender(eq(chatId), any(Pageable.class))).thenReturn(Collections.emptyList());
        List<MessageDto> result = service.getOptimizedChatMessages(chatId, userId, 0, 10);
        assertTrue(result.isEmpty());
    }
}
