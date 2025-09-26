package com.messenger.core.service;

import com.messenger.core.dto.ChatDto;
import com.messenger.core.dto.UserDto;
import com.messenger.core.dto.request.CreateChatRequest;
import com.messenger.core.model.Chat;
import com.messenger.core.model.Message;
import com.messenger.core.model.User;
import com.messenger.core.repository.ChatRepository;
import com.messenger.core.repository.MessageRepository;
import com.messenger.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public List<ChatDto> getUserChats(Long userId) {
        List<Chat> chats = chatRepository.findByParticipantId(userId);
        return chats.stream()
                .map(chat -> convertToDto(chat, userId))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ChatDto> getChatById(Long chatId, Long userId) {
        return chatRepository.findById(chatId)
                .map(chat -> convertToDto(chat, userId));
    }

    @Transactional
    public ChatDto createChat(CreateChatRequest request, Long creatorId) {
        Chat chat = new Chat();
        chat.setName(request.getName());
        chat.setDescription(request.getDescription());
        chat.setType(Chat.ChatType.valueOf(request.getType()));
        chat.setCreatedBy(creatorId);

        // Добавляем участников
        List<User> participants = userRepository.findAllById(request.getParticipantIds());

        // Добавляем создателя, если его нет в списке
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("Creator not found"));
        if (!participants.contains(creator)) {
            participants.add(creator);
        }

        chat.setParticipants(participants);

        Chat savedChat = chatRepository.save(chat);
        return convertToDto(savedChat, creatorId);
    }

    @Transactional
    public ChatDto createPrivateChat(Long userId1, Long userId2) {
        // Проверяем, существует ли уже приватный чат между пользователями
        Chat existingChat = chatRepository.findPrivateChatBetweenUsers(userId1, userId2);
        if (existingChat != null) {
            return convertToDto(existingChat, userId1);
        }

        // Создаем новый приватный чат
        Chat chat = new Chat();
        chat.setType(Chat.ChatType.PRIVATE);
        chat.setCreatedBy(userId1);

        List<User> participants = userRepository.findAllById(List.of(userId1, userId2));
        if (participants.size() != 2) {
            throw new RuntimeException("One or both users not found");
        }

        // Название приватного чата - имена участников
        String chatName = participants.stream()
                .map(user -> user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                .collect(Collectors.joining(", "));
        chat.setName(chatName);

        chat.setParticipants(participants);

        Chat savedChat = chatRepository.save(chat);
        return convertToDto(savedChat, userId1);
    }

    @Transactional
    public ChatDto addParticipant(Long chatId, Long userId, Long newParticipantId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        // Проверяем, является ли пользователь участником чата
        boolean isParticipant = chat.getParticipants().stream()
                .anyMatch(p -> p.getId().equals(userId));
        if (!isParticipant) {
            throw new RuntimeException("Access denied");
        }

        // Добавляем нового участника
        User newParticipant = userRepository.findById(newParticipantId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!chat.getParticipants().contains(newParticipant)) {
            chat.getParticipants().add(newParticipant);
            chatRepository.save(chat);
        }

        return convertToDto(chat, userId);
    }

    @Transactional
    public void removeParticipant(Long chatId, Long userId, Long participantToRemove) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        // Только создатель может удалять участников или пользователь может покинуть чат сам
        if (!chat.getCreatedBy().equals(userId) && !participantToRemove.equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        chat.getParticipants().removeIf(p -> p.getId().equals(participantToRemove));
        chatRepository.save(chat);
    }

    private ChatDto convertToDto(Chat chat, Long currentUserId) {
        ChatDto dto = new ChatDto();
        dto.setId(chat.getId());
        dto.setName(chat.getName());
        dto.setDescription(chat.getDescription());
        dto.setType(chat.getType());
        dto.setAvatarUrl(chat.getAvatarUrl());
        dto.setCreatedAt(chat.getCreatedAt());
        dto.setCreatedBy(chat.getCreatedBy());
        dto.setLastActivity(chat.getLastActivity());

        // Конвертируем участников
        List<UserDto> participantDtos = chat.getParticipants().stream()
                .map(this::convertUserToDto)
                .collect(Collectors.toList());
        dto.setParticipants(participantDtos);

        // Получаем последнее сообщение (упрощенно, без использования MessageService)
        List<Message> messages = messageRepository.findByChatIdOrderBySentAtDesc(chat.getId(),
                org.springframework.data.domain.PageRequest.of(0, 1)).getContent();
        if (!messages.isEmpty()) {
            Message lastMessage = messages.get(0);
            dto.setLastMessage(convertMessageToDto(lastMessage));
        }

        // Подсчитываем непрочитанные сообщения
        Long unreadCount = messageRepository.countUnreadMessagesInChat(chat.getId(), currentUserId);
        dto.setUnreadCount(unreadCount != null ? unreadCount : 0L);

        return dto;
    }

    private UserDto convertUserToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setDisplayName(user.getDisplayName());
        dto.setEmail(user.getEmail());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setBio(user.getBio());
        dto.setStatus(user.getStatus());
        dto.setLastSeen(user.getLastSeen());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setIsOnline(user.getIsOnline());
        dto.setLastSeenAt(user.getLastSeenAt());
        return dto;
    }

    private com.messenger.core.dto.MessageDto convertMessageToDto(Message message) {
        com.messenger.core.dto.MessageDto dto = new com.messenger.core.dto.MessageDto();
        dto.setId(message.getId());
        dto.setContent(message.getContent());
        dto.setType(message.getType());
        dto.setSentAt(message.getSentAt());
        dto.setChatId(message.getChat().getId());
        dto.setIsEdited(message.getIsEdited());
        dto.setEditedAt(message.getEditedAt());
        dto.setStatus(message.getStatus());
        dto.setSender(convertUserToDto(message.getSender()));
        return dto;
    }
}
