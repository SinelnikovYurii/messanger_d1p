package com.messenger.core.service;

import com.messenger.core.dto.ChatDto;
import com.messenger.core.dto.MessageDto;
import com.messenger.core.model.Chat;
import com.messenger.core.model.Message;
import com.messenger.core.model.User;
import com.messenger.core.repository.ChatRepository;
import com.messenger.core.repository.MessageRepository;
import com.messenger.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {

    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final UserService userService;
    private final MessageService messageService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Получить все чаты пользователя
     */
    @Transactional(readOnly = true)
    public List<ChatDto> getUserChats(Long userId) {
        List<Chat> chats = chatRepository.findChatsByUserId(userId);
        return chats.stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * Создать приватный чат между двумя пользователями
     */
    public ChatDto createPrivateChat(Long currentUserId, Long participantId) {
        if (currentUserId.equals(participantId)) {
            throw new IllegalArgumentException("Нельзя создать чат с самим собой");
        }

        // Проверяем, существует ли уже приватный чат между этими пользователями
        Optional<Chat> existingChat = chatRepository
            .findPrivateChatBetweenUsers(currentUserId, participantId);

        if (existingChat.isPresent()) {
            return convertToDto(existingChat.get());
        }

        // Получаем пользователей
        User currentUser = userRepository.findById(currentUserId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        User participant = userRepository.findById(participantId)
            .orElseThrow(() -> new RuntimeException("Собеседник не найден"));

        // Создаем новый приватный чат
        Chat chat = new Chat();
        chat.setChatType(Chat.ChatType.PRIVATE);
        chat.setCreatedBy(currentUser);
        chat.setParticipants(Set.of(currentUser, participant));

        Chat savedChat = chatRepository.save(chat);

        // Отправляем системное сообщение о создании чата
        sendSystemMessage(savedChat, "Чат создан");

        return convertToDto(savedChat);
    }

    /**
     * Создать групповой чат
     */
    public ChatDto createGroupChat(Long creatorId, ChatDto.CreateChatRequest request) {
        User creator = userRepository.findById(creatorId)
            .orElseThrow(() -> new RuntimeException("Создатель не найден"));

        // Получаем участников
        Set<User> participants = new HashSet<>();
        participants.add(creator); // Добавляем создателя

        if (request.getParticipantIds() != null) {
            List<User> requestedParticipants = userRepository.findAllById(request.getParticipantIds());
            participants.addAll(requestedParticipants);
        }

        // Создаем групповой чат
        Chat chat = new Chat();
        chat.setChatName(request.getChatName());
        chat.setChatType(Chat.ChatType.GROUP);
        chat.setChatDescription(request.getChatDescription());
        chat.setCreatedBy(creator);
        chat.setParticipants(participants);

        Chat savedChat = chatRepository.save(chat);

        // Отправляем системное сообщение о создании группового чата
        String systemMessage = String.format("Групповой чат '%s' создан пользователем %s",
            savedChat.getChatName(), creator.getUsername());
        sendSystemMessage(savedChat, systemMessage);

        return convertToDto(savedChat);
    }

    /**
     * Добавить участников в групповой чат
     */
    public ChatDto addParticipants(Long chatId, Long currentUserId, List<Long> userIds) {
        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new RuntimeException("Чат не найден"));

        if (chat.getChatType() != Chat.ChatType.GROUP) {
            throw new IllegalArgumentException("Можно добавлять участников только в групповые чаты");
        }

        // Проверяем, что текущий пользователь является участником чата
        boolean isParticipant = chat.getParticipants().stream()
            .anyMatch(p -> p.getId().equals(currentUserId));

        if (!isParticipant) {
            throw new IllegalArgumentException("У вас нет прав для добавления участников в этот чат");
        }

        // Получаем новых участников
        List<User> newParticipants = userRepository.findAllById(userIds);
        Set<User> currentParticipants = new HashSet<>(chat.getParticipants());

        List<String> addedUsernames = new ArrayList<>();
        for (User user : newParticipants) {
            if (!currentParticipants.contains(user)) {
                currentParticipants.add(user);
                addedUsernames.add(user.getUsername());
            }
        }

        chat.setParticipants(currentParticipants);
        Chat savedChat = chatRepository.save(chat);

        // Отправляем системное сообщение о добавлении участников
        if (!addedUsernames.isEmpty()) {
            User currentUser = userRepository.findById(currentUserId).orElse(null);
            String systemMessage = String.format("%s добавил(а) в чат: %s",
                currentUser != null ? currentUser.getUsername() : "Пользователь",
                String.join(", ", addedUsernames));
            sendSystemMessage(savedChat, systemMessage);

            // Отправляем уведомление через Kafka
            notifyParticipantsAboutChatUpdate(savedChat, "PARTICIPANTS_ADDED");
        }

        return convertToDto(savedChat);
    }

    /**
     * Покинуть чат
     */
    public void leaveChat(Long chatId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new RuntimeException("Чат не найден"));

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        if (chat.getChatType() == Chat.ChatType.PRIVATE) {
            throw new IllegalArgumentException("Нельзя покинуть приватный чат");
        }

        Set<User> participants = new HashSet<>(chat.getParticipants());
        if (participants.remove(user)) {
            chat.setParticipants(participants);
            chatRepository.save(chat);

            // Отправляем системное сообщение
            String systemMessage = String.format("%s покинул(а) чат", user.getUsername());
            sendSystemMessage(chat, systemMessage);

            // Если чат остался пустым, удаляем его
            if (participants.isEmpty()) {
                chatRepository.delete(chat);
            } else {
                notifyParticipantsAboutChatUpdate(chat, "PARTICIPANT_LEFT");
            }
        }
    }

    /**
     * Получить информацию о чате
     */
    @Transactional(readOnly = true)
    public ChatDto getChatInfo(Long chatId, Long currentUserId) {
        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new RuntimeException("Чат не найден"));

        // Проверяем, что пользователь является участником чата
        boolean isParticipant = chat.getParticipants().stream()
            .anyMatch(p -> p.getId().equals(currentUserId));

        if (!isParticipant) {
            throw new IllegalArgumentException("У вас нет доступа к этому чату");
        }

        return convertToDto(chat);
    }

    /**
     * Поиск чатов по названию
     */
    @Transactional(readOnly = true)
    public List<ChatDto> searchChats(String query, Long userId) {
        List<Chat> chats = chatRepository.findChatsByNameContaining(query);

        // Фильтруем чаты, к которым пользователь имеет доступ
        return chats.stream()
            .filter(chat -> chat.getParticipants().stream()
                .anyMatch(p -> p.getId().equals(userId)))
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * Отправить системное сообщение в чат
     */
    private void sendSystemMessage(Chat chat, String content) {
        Message systemMessage = new Message();
        systemMessage.setContent(content);
        systemMessage.setMessageType(Message.MessageType.SYSTEM);
        systemMessage.setChat(chat);
        systemMessage.setSender(null); // Системное сообщение не имеет отправителя

        messageRepository.save(systemMessage);

        // Обновляем время последнего сообщения в чате
        chat.setLastMessageAt(LocalDateTime.now());
        chatRepository.save(chat);
    }

    /**
     * Уведомить участников об изменениях в чате через Kafka
     */
    private void notifyParticipantsAboutChatUpdate(Chat chat, String eventType) {
        Map<String, Object> notification = Map.of(
            "type", "CHAT_UPDATE",
            "chatId", chat.getId(),
            "eventType", eventType,
            "timestamp", LocalDateTime.now()
        );

        kafkaTemplate.send("chat-updates", notification);
    }

    /**
     * Конвертировать Chat в ChatDto
     */
    private ChatDto convertToDto(Chat chat) {
        ChatDto dto = new ChatDto();
        dto.setId(chat.getId());
        dto.setChatName(chat.getChatName());
        dto.setChatType(chat.getChatType());
        dto.setChatDescription(chat.getChatDescription());
        dto.setChatAvatarUrl(chat.getChatAvatarUrl());
        dto.setCreatedAt(chat.getCreatedAt());
        dto.setLastMessageAt(chat.getLastMessageAt());

        if (chat.getCreatedBy() != null) {
            dto.setCreatedBy(userService.convertToDto(chat.getCreatedBy()));
        }

        if (chat.getParticipants() != null) {
            dto.setParticipants(chat.getParticipants().stream()
                .map(userService::convertToDto)
                .collect(Collectors.toList()));
        }

        // Получаем последнее сообщение
        if (chat.getMessages() != null && !chat.getMessages().isEmpty()) {
            Message lastMessage = chat.getMessages().stream()
                .filter(m -> !m.getIsDeleted())
                .max(Comparator.comparing(Message::getCreatedAt))
                .orElse(null);

            if (lastMessage != null) {
                dto.setLastMessage(messageService.convertToDto(lastMessage));
            }
        }

        return dto;
    }
}
