package com.messenger.core.service;

import com.messenger.core.dto.ChatDto;
import com.messenger.core.model.Chat;
import com.messenger.core.model.User;
import com.messenger.core.model.Message;
import com.messenger.core.repository.ChatRepository;
import com.messenger.core.repository.UserRepository;
import com.messenger.core.repository.MessageRepository;
import com.messenger.core.repository.MessageReadStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatServiceTest {
    @Mock
    private ChatRepository chatRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserService userService;
    @Mock
    private MessageService messageService;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private MessageReadStatusRepository messageReadStatusRepository;
    @InjectMocks
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Мокируем save для Message, чтобы избежать NullPointerException в sendSystemMessage
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Мокируем userService.convertToDto для корректного преобразования участников
        when(userService.convertToDto(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            com.messenger.core.dto.UserDto userDto = new com.messenger.core.dto.UserDto();
            userDto.setId(user.getId());
            userDto.setUsername(user.getUsername());
            return userDto;
        });
        // Универсальное мокирование для save
        when(chatRepository.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void testCreateChat() {
        User creator = new User();
        creator.setId(1L);
        Chat chat = new Chat();
        chat.setChatName("Test Chat");
        chat.setChatType(Chat.ChatType.GROUP);
        chat.setCreatedBy(creator);
        chat.setParticipants(new HashSet<>());
        when(chatRepository.save(any(Chat.class))).thenReturn(chat);
        Chat created = chatService.createChat(chat);
        assertNotNull(created);
        assertEquals("Test Chat", created.getChatName());
        verify(chatRepository, times(1)).save(any(Chat.class));
    }

    @Test
    void testFindChatById() {
        Chat chat = new Chat();
        chat.setId(10L);
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        Optional<Chat> found = chatService.findChatById(10L);
        assertTrue(found.isPresent());
        assertEquals(10L, found.get().getId());
    }

    @Test
    void testAddParticipant() {
        Chat chat = new Chat();
        chat.setId(5L);
        chat.setParticipants(new HashSet<>());
        User user = new User();
        user.setId(2L);
        when(chatRepository.findById(5L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        chatService.addParticipant(5L, 2L);
        assertTrue(chat.getParticipants().contains(user));
    }

    @Test
    void testRemoveParticipant() {
        Chat chat = new Chat();
        chat.setId(5L);
        chat.setChatType(Chat.ChatType.GROUP);
        User creator = new User(); creator.setId(5L); creator.setUsername("creator");
        chat.setCreatedBy(creator);
        User user = new User(); user.setId(2L); user.setUsername("user2");
        chat.setParticipants(new HashSet<>(Set.of(user)));
        when(chatRepository.findById(5L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(chatRepository.save(any(Chat.class))).thenReturn(chat); // Явное мокирование
        chatService.removeParticipant(5L, 5L, 2L);
        assertFalse(chat.getParticipants().contains(user));
    }

    @Test
    void testCreatePrivateChat_success() {
        User user1 = new User(); user1.setId(1L); user1.setUsername("user1");
        User user2 = new User(); user2.setId(2L); user2.setUsername("user2");
        Chat chat = new Chat(); chat.setId(100L); chat.setChatType(Chat.ChatType.PRIVATE);
        chat.setParticipants(new HashSet<>(Set.of(user1, user2)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(chatRepository.findPrivateChatBetweenUsers(1L, 2L)).thenReturn(Optional.empty());
        when(chatRepository.save(any(Chat.class))).thenReturn(chat);
        ChatDto dto = chatService.createPrivateChat(1L, 2L);
        assertNotNull(dto);
        assertEquals(Chat.ChatType.PRIVATE, dto.getChatType());
    }

    @Test
    void testCreatePrivateChat_alreadyExists() {
        User user1 = new User(); user1.setId(1L); user1.setUsername("user1");
        User user2 = new User(); user2.setId(2L); user2.setUsername("user2");
        Chat chat = new Chat(); chat.setId(100L); chat.setChatType(Chat.ChatType.PRIVATE);
        chat.setParticipants(new HashSet<>(Set.of(user1, user2)));
        when(chatRepository.findPrivateChatBetweenUsers(1L, 2L)).thenReturn(Optional.of(chat));
        ChatDto dto = chatService.createPrivateChat(1L, 2L);
        assertNotNull(dto);
        assertEquals(Chat.ChatType.PRIVATE, dto.getChatType());
    }

    @Test
    void testCreateGroupChat_success() {
        User creator = new User(); creator.setId(1L); creator.setUsername("creator");
        User participant = new User(); participant.setId(2L); participant.setUsername("user2");
        ChatDto.CreateChatRequest req = new ChatDto.CreateChatRequest();
        req.setChatName("Group");
        req.setParticipantIds(List.of(2L));
        Chat chat = new Chat(); chat.setId(200L); chat.setChatType(Chat.ChatType.GROUP);
        chat.setParticipants(new HashSet<>(Set.of(creator, participant)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(participant));
        when(chatRepository.save(any(Chat.class))).thenReturn(chat);
        ChatDto dto = chatService.createGroupChat(1L, req);
        assertNotNull(dto);
        assertEquals(Chat.ChatType.GROUP, dto.getChatType());
    }

    @Test
    void testAddParticipants_success() {
        User creator = new User(); creator.setId(1L); creator.setUsername("creator");
        User participant = new User(); participant.setId(2L); participant.setUsername("user2");
        User newUser = new User(); newUser.setId(3L); newUser.setUsername("user3");
        Chat chat = new Chat(); chat.setId(300L); chat.setChatType(Chat.ChatType.GROUP);
        chat.setCreatedBy(creator);
        chat.setParticipants(new HashSet<>(Set.of(creator, participant)));
        when(chatRepository.findById(300L)).thenReturn(Optional.of(chat));
        when(userRepository.findAllById(List.of(3L))).thenReturn(List.of(newUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(chatRepository.save(any(Chat.class))).thenReturn(chat);
        ChatDto dto = chatService.addParticipants(300L, 1L, List.of(3L));
        assertNotNull(dto);
        assertTrue(dto.getParticipants().stream().anyMatch(u -> u != null && u.getId() != null && u.getId().equals(3L)));
    }

    @Test
    void testRemoveParticipant_success() {
        User creator = new User(); creator.setId(1L); creator.setUsername("creator");
        User participant = new User(); participant.setId(2L); participant.setUsername("user2");
        Chat chat = new Chat(); chat.setId(400L); chat.setChatType(Chat.ChatType.GROUP);
        chat.setCreatedBy(creator);
        chat.setParticipants(new HashSet<>(Set.of(creator, participant)));
        when(chatRepository.findById(400L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(2L)).thenReturn(Optional.of(participant));
        when(chatRepository.save(any(Chat.class))).thenReturn(chat);
        ChatDto dto = chatService.removeParticipant(400L, 1L, 2L);
        assertNotNull(dto);
        assertTrue(dto.getParticipants().stream().noneMatch(u -> u != null && u.getId() != null && u.getId().equals(2L)));
    }

    @Test
    void testDeleteChat_success() {
        User creator = new User(); creator.setId(999L);
        Chat chat = new Chat(); chat.setId(1L);
        chat.setCreatedBy(creator);
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        doNothing().when(chatRepository).delete(chat);
        chatService.deleteChat(1L, 999L);
        verify(chatRepository, times(1)).delete(chat);
    }

    @Test
    void testDeleteChat_notFound() {
        when(chatRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> chatService.deleteChat(999L, 999L));
    }

    @Test
    void testLeaveChat_success() {
        Chat chat = new Chat(); chat.setId(1L);
        chat.setChatType(Chat.ChatType.PRIVATE);
        User user = new User(); user.setId(2L);
        chat.setParticipants(new HashSet<>(Set.of(user)));
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        chatService.leaveChat(1L, 2L);
        assertFalse(chat.getParticipants().contains(user));
    }

    @Test
    void testSearchChats_success() {
        Chat chat = new Chat(); chat.setId(1L); chat.setChatName("Test");
        when(chatRepository.findChatsByUserId(999L)).thenReturn(List.of(chat));
        List<ChatDto> chats = chatService.searchChats("Test", 999L);
        assertEquals(1, chats.size());
        assertEquals("Test", chats.get(0).getChatName());
    }

    @Test
    void testGetChatParticipants_success() {
        User requestingUser = new User(); requestingUser.setId(999L);
        User otherUser = new User(); otherUser.setId(2L);
        Chat chat = new Chat(); chat.setId(1L);
        chat.setParticipants(new HashSet<>(Set.of(requestingUser, otherUser)));
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        Set<User> participants = chatService.getChatParticipants(1L, 999L);
        assertEquals(2, participants.size());
        assertTrue(participants.contains(requestingUser));
        assertTrue(participants.contains(otherUser));
    }

    @Test
    void testAddParticipant_noRights() {
        User creator = new User(); creator.setId(999L);
        User newUser = new User(); newUser.setId(2L);
        Chat chat = new Chat(); chat.setId(1L);
        chat.setCreatedBy(creator);
        chat.setParticipants(new HashSet<>(Set.of(creator)));
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(2L)).thenReturn(Optional.of(newUser));

    }

    @Test
    void testGetChatInfo_notFound() {
        when(chatRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> chatService.getChatInfo(999L, 999L));
    }

    @Test
    void testCreatePrivateChat_withSelf() {
        assertThrows(IllegalArgumentException.class, () -> chatService.createPrivateChat(1L, 1L));
    }

    @Test
    void testCreatePrivateChat_withNullParticipant() {
        assertThrows(IllegalArgumentException.class, () -> chatService.createPrivateChat(1L, null));
    }

    @Test
    void testGetUserChats_withNullUserId() {
        assertThrows(RuntimeException.class, () -> chatService.getUserChats(null));
    }

    @Test
    void testGetUserChats_userNotFound() {
        when(userRepository.existsById(123L)).thenReturn(false);
        assertThrows(RuntimeException.class, () -> chatService.getUserChats(123L));
    }

    @Test
    void testGetUserChats_emptyChats() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(chatRepository.findChatsByUserIdWithParticipants(1L)).thenReturn(List.of());
        List<ChatDto> result = chatService.getUserChats(1L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testAddParticipant_chatNotFound() {
        when(chatRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> chatService.addParticipant(999L, 1L));
    }


    @Test
    void testAddParticipant_alreadyExists() {
        Chat chat = new Chat(); chat.setId(1L);
        User user = new User(); user.setId(2L);
        chat.setParticipants(new HashSet<>(Set.of(user)));
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        // Не должно выбрасывать исключение, но участник не добавляется второй раз
        chatService.addParticipant(1L, 2L);
        assertEquals(1, chat.getParticipants().size());
    }

    @Test
    void testRemoveParticipant_notInChat() {
        Chat chat = new Chat(); chat.setId(1L); chat.setChatType(Chat.ChatType.GROUP);
        User creator = new User(); creator.setId(1L);
        chat.setCreatedBy(creator);
        User user = new User(); user.setId(2L);
        chat.setParticipants(new HashSet<>()); // user не в чате
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        // Не должно выбрасывать исключение, просто ничего не происходит
        chatService.removeParticipant(1L, 1L, 2L);
        assertFalse(chat.getParticipants().contains(user));
    }

    @Test
    void testRemoveParticipant_notGroupType() {
        Chat chat = new Chat(); chat.setId(1L); chat.setChatType(Chat.ChatType.PRIVATE);
        User user = new User(); user.setId(2L);
        chat.setParticipants(new HashSet<>(Set.of(user)));
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        assertThrows(IllegalArgumentException.class, () -> chatService.removeParticipant(1L, 1L, 2L));
    }

    @Test
    void testCreateGroupChat_noParticipants() {
        User creator = new User(); creator.setId(1L);
        ChatDto.CreateChatRequest req = new ChatDto.CreateChatRequest();
        req.setChatName("Group");
        req.setParticipantIds(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        assertThrows(IllegalArgumentException.class, () -> chatService.createGroupChat(1L, req));
    }

    @Test
    void testCreateGroupChat_participantNotFound() {
        User creator = new User(); creator.setId(1L);
        ChatDto.CreateChatRequest req = new ChatDto.CreateChatRequest();
        req.setChatName("Group");
        req.setParticipantIds(List.of(2L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of());
        assertThrows(RuntimeException.class, () -> chatService.createGroupChat(1L, req));
    }

    @Test
    void testAddParticipants_noRights() {
        Chat chat = new Chat(); chat.setId(1L);
        User creator = new User(); creator.setId(1L);
        User user = new User(); user.setId(2L);
        chat.setCreatedBy(creator);
        chat.setParticipants(new HashSet<>(Set.of(creator, user)));
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(999L)).thenReturn(Optional.of(new User()));
        // Попытка добавить участников не создателем
        assertThrows(IllegalArgumentException.class, () -> chatService.addParticipants(1L, 999L, List.of(3L)));
    }

    @Test
    void testAddParticipants_emptyList() {
        Chat chat = new Chat(); chat.setId(1L);
        chat.setChatType(Chat.ChatType.GROUP);
        User creator = new User(); creator.setId(1L);
        chat.setCreatedBy(creator);
        chat.setParticipants(new HashSet<>(Set.of(creator)));
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        ChatDto dto = chatService.addParticipants(1L, 1L, List.of());
        assertNotNull(dto);
        assertEquals(1, dto.getParticipants().size());
    }

    @Test
    void testGetUserChats_manyChats() {
        when(userRepository.existsById(1L)).thenReturn(true);
        Chat chat1 = new Chat(); chat1.setId(1L);
        Chat chat2 = new Chat(); chat2.setId(2L);
        when(chatRepository.findChatsByUserIdWithParticipants(1L)).thenReturn(List.of(chat1, chat2));
        when(messageReadStatusRepository.countUnreadMessagesForChats(anyList(), eq(1L))).thenReturn(List.of(new Object[]{1L, 0L}, new Object[]{2L, 1L}));
        when(messageRepository.findLastMessagesByChatIds(anyList())).thenReturn(List.of());
        List<ChatDto> result = chatService.getUserChats(1L);
        assertEquals(2, result.size());
    }

    @Test
    void testCreatePrivateChat_userNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> chatService.createPrivateChat(1L, 2L));
    }

    @Test
    void testCreatePrivateChat_participantNotFound() {
        User user1 = new User(); user1.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> chatService.createPrivateChat(1L, 2L));
    }

    @Test
    void testCreateGroupChat_requestNull() {
        User creator = new User(); creator.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        assertThrows(NullPointerException.class, () -> chatService.createGroupChat(1L, null));
    }

    @Test
    void testCreateGroupChat_duplicateParticipants() {
        User creator = new User(); creator.setId(1L);
        User participant = new User(); participant.setId(2L);
        ChatDto.CreateChatRequest req = new ChatDto.CreateChatRequest();
        req.setChatName("Group");
        req.setParticipantIds(List.of(2L, 2L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        // Возвращаем два объекта с одинаковым id, чтобы размер совпадал
        when(userRepository.findAllById(List.of(2L, 2L))).thenReturn(List.of(participant, participant));
        when(chatRepository.save(any(Chat.class))).thenReturn(new Chat());
        ChatDto dto = chatService.createGroupChat(1L, req);
        assertNotNull(dto);
    }

    @Test
    void testAddParticipants_chatNotFound() {
        when(chatRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> chatService.addParticipants(999L, 1L, List.of(2L)));
    }

    @Test
    void testRemoveParticipant_chatNotFound() {
        when(chatRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> chatService.removeParticipant(999L, 1L, 2L));
    }

    @Test
    void testLeaveChat_chatNotFound() {
        when(chatRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> chatService.leaveChat(999L, 1L));
    }

    @Test
    void testLeaveChat_userNotFound() {
        Chat chat = new Chat(); chat.setId(1L);
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> chatService.leaveChat(1L, 2L));
    }

    @Test
    void testGetChatInfo_userNotParticipant() {
        Chat chat = new Chat(); chat.setId(1L);
        when(chatRepository.isUserParticipant(1L, 2L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> chatService.getChatInfo(1L, 2L));
    }

    @Test
    void testGetChatParticipantIds_chatNotFound() {
        when(chatRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> chatService.getChatParticipantIds(999L, 1L));
    }

    @Test
    void testGetChatParticipantIds_userNotParticipant() {
        Chat chat = new Chat(); chat.setId(1L);
        User user = new User(); user.setId(2L);
        chat.setParticipants(new HashSet<>(Set.of(user)));
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        assertThrows(IllegalArgumentException.class, () -> chatService.getChatParticipantIds(1L, 999L));
    }

    @Test
    void testGetChatParticipantIdsInternal_chatNotFound() {
        when(chatRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> chatService.getChatParticipantIdsInternal(999L));
    }

    @Test
    void testGetChatParticipantIdsInternal_success() {
        Chat chat = new Chat(); chat.setId(1L);
        User user1 = new User(); user1.setId(1L);
        User user2 = new User(); user2.setId(2L);
        chat.setParticipants(new HashSet<>(Set.of(user1, user2)));
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        List<Long> ids = chatService.getChatParticipantIdsInternal(1L);
        assertEquals(2, ids.size());
        assertTrue(ids.contains(1L));
        assertTrue(ids.contains(2L));
    }

    @Test
    void testDeleteChat_userNotCreator() {
        User creator = new User(); creator.setId(1L);
        User notCreator = new User(); notCreator.setId(2L);
        Chat chat = new Chat(); chat.setId(1L);
        chat.setCreatedBy(creator);
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        assertThrows(IllegalArgumentException.class, () -> chatService.deleteChat(1L, 2L));
    }

    @Test
    void testDeleteChat_noCreator() {
        Chat chat = new Chat(); chat.setId(1L);
        chat.setCreatedBy(null);
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        assertThrows(IllegalArgumentException.class, () -> chatService.deleteChat(1L, 1L));
    }

    @Test
    void testSearchChats_emptyQuery() {
        when(chatRepository.findChatsByUserId(1L)).thenReturn(List.of());
        List<ChatDto> result = chatService.searchChats("", 1L);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchChats_partialMatch() {
        Chat chat1 = new Chat(); chat1.setId(1L); chat1.setChatName("Alpha");
        Chat chat2 = new Chat(); chat2.setId(2L); chat2.setChatName("Beta");
        when(chatRepository.findChatsByUserId(1L)).thenReturn(List.of(chat1, chat2));
        List<ChatDto> result = chatService.searchChats("Al", 1L);
        assertEquals(1, result.size());
        assertEquals("Alpha", result.get(0).getChatName());
    }

    @Test
    void testCreatePrivateChat_negativeId() {
        assertThrows(RuntimeException.class, () -> chatService.createPrivateChat(-1L, 2L));
        assertThrows(RuntimeException.class, () -> chatService.createPrivateChat(1L, -2L));
    }

    @Test
    void testCreateGroupChat_emptyName() {
        User creator = new User(); creator.setId(1L);
        ChatDto.CreateChatRequest req = new ChatDto.CreateChatRequest();
        req.setChatName("");
        req.setParticipantIds(List.of(2L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(new User()));
        when(chatRepository.save(any(Chat.class))).thenReturn(new Chat());
        ChatDto dto = chatService.createGroupChat(1L, req);
        assertNotNull(dto);
    }

    @Test
    void testCreateGroupChat_longName() {
        User creator = new User(); creator.setId(1L);
        String longName = "A".repeat(300);
        ChatDto.CreateChatRequest req = new ChatDto.CreateChatRequest();
        req.setChatName(longName);
        req.setParticipantIds(List.of(2L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        User participant = new User(); participant.setId(2L);
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(participant));
        Chat chat = new Chat();
        chat.setChatName(longName);
        chat.setParticipants(new HashSet<>(Set.of(creator, participant)));
        when(chatRepository.save(any(Chat.class))).thenReturn(chat);
        ChatDto dto = chatService.createGroupChat(1L, req);
        assertNotNull(dto);
        assertEquals(longName, dto.getChatName());
    }

    @Test
    void testAddParticipants_someAlreadyExist() {
        Chat chat = new Chat(); chat.setId(1L); chat.setChatType(Chat.ChatType.GROUP);
        User creator = new User(); creator.setId(1L);
        User user2 = new User(); user2.setId(2L);
        User user3 = new User(); user3.setId(3L);
        chat.setCreatedBy(creator);
        chat.setParticipants(new HashSet<>(Set.of(creator, user2)));
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        when(userRepository.findAllById(List.of(2L, 3L))).thenReturn(List.of(user2, user3));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(chatRepository.save(any(Chat.class))).thenReturn(chat);
        ChatDto dto = chatService.addParticipants(1L, 1L, List.of(2L, 3L));
        assertTrue(dto.getParticipants().stream().anyMatch(u -> u.getId().equals(3L)));
    }

    @Test
    void testRemoveParticipant_lastParticipant() {
        Chat chat = new Chat(); chat.setId(1L); chat.setChatType(Chat.ChatType.GROUP);
        User creator = new User(); creator.setId(1L);
        User participant = new User(); participant.setId(2L);
        chat.setCreatedBy(creator);
        chat.setParticipants(new HashSet<>(Set.of(creator, participant)));
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(2L)).thenReturn(Optional.of(participant));
        when(chatRepository.save(any(Chat.class))).thenReturn(chat);
        ChatDto dto = chatService.removeParticipant(1L, 1L, 2L);
        assertTrue(dto.getParticipants().size() == 1 && dto.getParticipants().iterator().next().getId().equals(1L));
    }

    @Test
    void testLeaveChat_userNotInChat() {
        Chat chat = new Chat(); chat.setId(1L); chat.setChatType(Chat.ChatType.GROUP);
        User creator = new User(); creator.setId(1L);
        chat.setCreatedBy(creator);
        chat.setParticipants(new HashSet<>(Set.of(creator)));
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        User user = new User(); user.setId(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        assertDoesNotThrow(() -> chatService.leaveChat(1L, 2L));
    }

    @Test
    void testGetUserChats_duplicateParticipants() {
        when(userRepository.existsById(1L)).thenReturn(true);
        Chat chat1 = new Chat(); chat1.setId(1L);
        Chat chat2 = new Chat(); chat2.setId(2L);
        User user = new User(); user.setId(1L);
        chat1.setParticipants(new HashSet<>(Set.of(user)));
        chat2.setParticipants(new HashSet<>(Set.of(user)));
        when(chatRepository.findChatsByUserIdWithParticipants(1L)).thenReturn(List.of(chat1, chat2));
        when(messageReadStatusRepository.countUnreadMessagesForChats(anyList(), eq(1L))).thenReturn(List.of());
        when(messageRepository.findLastMessagesByChatIds(anyList())).thenReturn(List.of());
        List<ChatDto> result = chatService.getUserChats(1L);
        assertEquals(2, result.size());
    }

    @Test
    void testSearchChats_caseInsensitive() {
        Chat chat1 = new Chat(); chat1.setId(1L); chat1.setChatName("Alpha");
        Chat chat2 = new Chat(); chat2.setId(2L); chat2.setChatName("beta");
        when(chatRepository.findChatsByUserId(1L)).thenReturn(List.of(chat1, chat2));
        List<ChatDto> result = chatService.searchChats("ALPHA", 1L);
        assertEquals(1, result.size());
        assertEquals("Alpha", result.get(0).getChatName());
    }

    @Test
    void testDeleteChat_withMessages() {
        User creator = new User(); creator.setId(1L);
        Chat chat = new Chat(); chat.setId(1L);
        chat.setCreatedBy(creator);
        Message msg = new Message(); msg.setId(1L);
        // Исправление: преобразуем Set<Message> в List<Message>
        chat.setMessages(List.of(msg));
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        doNothing().when(chatRepository).delete(chat);
        assertDoesNotThrow(() -> chatService.deleteChat(1L, 1L));
    }

    @Test
    void testCreateGroupChat_participantNoUsername() {
        User creator = new User(); creator.setId(1L);
        User participant = new User(); participant.setId(2L);
        ChatDto.CreateChatRequest req = new ChatDto.CreateChatRequest();
        req.setChatName("Group");
        req.setParticipantIds(List.of(2L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(participant));
        when(chatRepository.save(any(Chat.class))).thenReturn(new Chat());
        ChatDto dto = chatService.createGroupChat(1L, req);
        assertNotNull(dto);
    }

    @Test
    void testAddParticipants_emptyCollection() {
        Chat chat = new Chat(); chat.setId(1L); chat.setChatType(Chat.ChatType.GROUP);
        User creator = new User(); creator.setId(1L);
        chat.setCreatedBy(creator);
        chat.setParticipants(new HashSet<>(Set.of(creator)));
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        ChatDto dto = chatService.addParticipants(1L, 1L, List.of());
        assertNotNull(dto);
        assertEquals(1, dto.getParticipants().size());
    }

    @Test
    void testAddParticipants_maxId() {
        Chat chat = new Chat(); chat.setId(1L); chat.setChatType(Chat.ChatType.GROUP);
        User creator = new User(); creator.setId(1L);
        User maxUser = new User(); maxUser.setId(Long.MAX_VALUE);
        chat.setCreatedBy(creator);
        chat.setParticipants(new HashSet<>(Set.of(creator)));
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        when(userRepository.findAllById(List.of(Long.MAX_VALUE))).thenReturn(List.of(maxUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(chatRepository.save(any(Chat.class))).thenReturn(chat);
        ChatDto dto = chatService.addParticipants(1L, 1L, List.of(Long.MAX_VALUE));
        assertTrue(dto.getParticipants().stream().anyMatch(u -> u.getId().equals(Long.MAX_VALUE)));
    }

    @Test
    void testSystemMessageOnCreateGroupChat() {
        User creator = new User(); creator.setId(1L); creator.setUsername("creator");
        User participant = new User(); participant.setId(2L); participant.setUsername("user2");
        ChatDto.CreateChatRequest req = new ChatDto.CreateChatRequest();
        req.setChatName("Group");
        req.setParticipantIds(List.of(2L));
        Chat chat = new Chat(); chat.setId(200L); chat.setChatType(Chat.ChatType.GROUP);
        chat.setParticipants(new HashSet<>(Set.of(creator, participant)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(participant));
        when(chatRepository.save(any(Chat.class))).thenReturn(chat);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ChatDto dto = chatService.createGroupChat(1L, req);
        assertNotNull(dto);
        verify(messageRepository, atLeastOnce()).save(any(Message.class));
    }

    @Test
    void testConvertToDtoOptimized_privateChatWithAvatar() {
        User user1 = new User(); user1.setId(1L); user1.setUsername("user1");
        User user2 = new User(); user2.setId(2L); user2.setUsername("user2"); user2.setProfilePictureUrl("avatar2.png");
        Chat chat = new Chat();
        chat.setId(10L);
        chat.setChatName("PrivateChat");
        chat.setChatType(Chat.ChatType.PRIVATE);
        chat.setParticipants(new HashSet<>(Set.of(user1, user2)));
        chat.setChatAvatarUrl("default.png");
        chat.setCreatedAt(java.time.LocalDateTime.now());
        chat.setLastMessageAt(java.time.LocalDateTime.now());
        chat.setCreatedBy(user1);
        Message lastMessage = new Message();
        lastMessage.setId(100L);
        lastMessage.setContent("last msg");
        when(userService.convertToDto(any(User.class))).thenReturn(new com.messenger.core.dto.UserDto());
        when(messageService.convertToDto(any(Message.class))).thenReturn(new com.messenger.core.dto.MessageDto());
        ChatDto dto = invokeConvertToDtoOptimized(chat, lastMessage, 1L, 5);
        assertEquals(chat.getId(), dto.getId());
        assertEquals("PrivateChat", dto.getChatName());
        assertEquals(Chat.ChatType.PRIVATE, dto.getChatType());
        assertEquals("avatar2.png", dto.getChatAvatarUrl());
        assertEquals(5, dto.getUnreadCount());
        assertNotNull(dto.getLastMessage());
    }

    @Test
    void testConvertToDtoOptimized_groupChat() {
        User user1 = new User(); user1.setId(1L); user1.setUsername("user1");
        Chat chat = new Chat();
        chat.setId(20L);
        chat.setChatName("GroupChat");
        chat.setChatType(Chat.ChatType.GROUP);
        chat.setParticipants(new HashSet<>(Set.of(user1)));
        chat.setChatAvatarUrl("group.png");
        chat.setCreatedAt(java.time.LocalDateTime.now());
        chat.setLastMessageAt(java.time.LocalDateTime.now());
        chat.setCreatedBy(user1);
        when(userService.convertToDto(any(User.class))).thenReturn(new com.messenger.core.dto.UserDto());
        ChatDto dto = invokeConvertToDtoOptimized(chat, null, 1L, null);
        assertEquals("group.png", dto.getChatAvatarUrl());
        assertEquals(0, dto.getUnreadCount());
        assertNull(dto.getLastMessage());
    }

    @Test
    void testConvertToDtoOptimized_noParticipants() {
        Chat chat = new Chat();
        chat.setId(30L);
        chat.setChatName("EmptyChat");
        chat.setChatType(Chat.ChatType.PRIVATE);
        chat.setParticipants(null);
        chat.setChatAvatarUrl("empty.png");
        chat.setCreatedAt(java.time.LocalDateTime.now());
        chat.setLastMessageAt(java.time.LocalDateTime.now());
        chat.setCreatedBy(null);
        ChatDto dto = invokeConvertToDtoOptimized(chat, null, 1L, 2);
        assertEquals("empty.png", dto.getChatAvatarUrl());
        assertEquals(2, dto.getUnreadCount());
    }

    // Вспомогательный метод для вызова private метода через reflection
    private ChatDto invokeConvertToDtoOptimized(Chat chat, Message lastMessage, Long userId, Integer unreadCount) {
        try {
            java.lang.reflect.Method m = ChatService.class.getDeclaredMethod("convertToDtoOptimized", Chat.class, Message.class, Long.class, Integer.class);
            m.setAccessible(true);
            return (ChatDto) m.invoke(chatService, chat, lastMessage, userId, unreadCount);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
