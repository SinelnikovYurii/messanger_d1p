package com.messenger.core.service;

import com.messenger.core.dto.ChatDto;
import com.messenger.core.dto.MessageDto;
import com.messenger.core.model.Chat;
import com.messenger.core.model.Message;
import com.messenger.core.model.User;
import com.messenger.core.repository.ChatRepository;
import com.messenger.core.repository.MessageRepository;
import com.messenger.core.repository.UserRepository;
import com.messenger.core.repository.MessageReadStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для управления чатами, их участниками и сообщениями.
 */
@Slf4j
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
    private final MessageReadStatusRepository messageReadStatusRepository;

    /**
     * Получить все чаты пользователя
     * @param userId ID пользователя
     * @return список чатов
     */
    @Transactional(readOnly = true)
    public List<ChatDto> getUserChats(Long userId) {
        try {
            if (userId == null) {
                throw new IllegalArgumentException("ID пользователя не может быть пустым");
            }

            log.info("=== НАЧАЛО getUserChats для пользователя {} ===", userId);

            // Проверяем существование пользователя
            if (!userRepository.existsById(userId)) {
                throw new IllegalArgumentException("Пользователь с ID " + userId + " не найден");
            }

            // Используем оптимизированный запрос с JOIN FETCH
            List<Chat> chats = chatRepository.findChatsByUserIdWithParticipants(userId);
            log.info("Загружено {} чатов для пользователя {}", chats.size(), userId);

            if (chats.isEmpty()) {
                return List.of();
            }

            // Предварительно загружаем последние сообщения для всех чатов одним запросом
            Map<Long, Message> lastMessages = getLastMessagesForChats(chats);
            log.info("Загружены последние сообщения для {} чатов", lastMessages.size());

            // ИСПРАВЛЕНО: Batch-загрузка счетчиков непрочитанных сообщений одним запросом
            Map<Long, Integer> unreadCounts = getUnreadCountsForChats(chats, userId);
            log.info("Загружены счетчики непрочитанных для {} чатов", unreadCounts.size());

            List<ChatDto> result = chats.stream()
                .map(chat -> convertToDtoOptimized(chat, lastMessages.get(chat.getId()), userId, unreadCounts.get(chat.getId())))
                .collect(Collectors.toList());

            log.info("=== ЗАВЕРШЕНО getUserChats: возвращено {} DTO ===", result.size());

            // Логируем детали первого чата для отладки
            if (!result.isEmpty()) {
                ChatDto firstChat = result.get(0);
                log.info("Пример первого чата: ID={}, Name={}, Type={}, AvatarUrl={}, UnreadCount={}",
                    firstChat.getId(), firstChat.getChatName(), firstChat.getChatType(),
                    firstChat.getChatAvatarUrl(), firstChat.getUnreadCount());
            }

            return result;
        } catch (Exception e) {
            // Логирование ошибки с подробной информацией
            log.error("Ошибка при получении чатов пользователя {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при получении чатов пользователя: " + e.getMessage(), e);
        }
    }

    /**
     * Создать приватный чат между двумя пользователями
     * @param currentUserId ID текущего пользователя
     * @param participantId ID второго участника
     * @return DTO чата
     */
    public ChatDto createPrivateChat(Long currentUserId, Long participantId) {
        if (currentUserId.equals(participantId)) {
            throw new IllegalArgumentException("Нельзя создать чат с самим собой");
        }

        if (participantId == null) {
            throw new IllegalArgumentException("ID участника не может быть пустым");
        }

        // Проверяем, существует ли уже приватный чат между этими пользователями
        Optional<Chat> existingChat = chatRepository
            .findPrivateChatBetweenUsers(currentUserId, participantId);

        if (existingChat.isPresent()) {
            return convertToDto(existingChat.get());
        }

        try {
            // Получаем пользователей
            User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("Текущий пользователь не найден"));
            User participant = userRepository.findById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("Собеседник не найден"));

            // Создаем новый приватный чат
            Chat chat = new Chat();
            chat.setChatType(Chat.ChatType.PRIVATE);
            chat.setCreatedBy(currentUser);

            // Используем HashSet вместо Set.of для большей надежности
            Set<User> participants = new HashSet<>();
            participants.add(currentUser);
            participants.add(participant);
            chat.setParticipants(participants);

            // Устанавливаем имя чата (для удобства отображения)
            chat.setChatName(participant.getUsername());

            Chat savedChat = chatRepository.save(chat);

            // Отправляем системное сообщение о создании чата
            sendSystemMessage(savedChat, "Чат создан");

            // Уведомляем участников о создании чата через Kafka
            notifyParticipantsAboutChatUpdate(savedChat, "CHAT_CREATED");

            return convertToDto(savedChat);
        } catch (Exception e) {
            // Логирование ошибки с подробной информацией
            String errorMessage = "Ошибка при создании приватного чата: " + e.getMessage();
            System.err.println(errorMessage);
            e.printStackTrace();
            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * Создать групповой чат
     */
    public ChatDto createGroupChat(Long creatorId, ChatDto.CreateChatRequest request) {
        User creator = userRepository.findById(creatorId)
            .orElseThrow(() -> new RuntimeException("Создатель не найден"));

        // Проверка на пустой список участников
        if (request.getParticipantIds() == null || request.getParticipantIds().isEmpty()) {
            throw new IllegalArgumentException("Список участников не может быть пустым");
        }

        // Получаем участников
        Set<User> participants = new HashSet<>();
        participants.add(creator); // Добавляем создателя

        List<User> requestedParticipants = userRepository.findAllById(request.getParticipantIds());
        if (requestedParticipants.size() != request.getParticipantIds().size()) {
            throw new RuntimeException("Один или несколько участников не найдены");
        }
        participants.addAll(requestedParticipants);

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

        // Уведомляем всех участников о создании группового чата
        notifyParticipantsAboutChatUpdate(savedChat, "CHAT_CREATED");

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

        // Если были добавлены новые участники
        if (!addedUsernames.isEmpty()) {
            chat.setParticipants(currentParticipants);
            Chat updatedChat = chatRepository.save(chat);

            // Отправляем системное сообщение о добавлении участников
            User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

            String message = String.format("%s добавил(а) пользователей: %s",
                currentUser.getUsername(), String.join(", ", addedUsernames));
            sendSystemMessage(updatedChat, message);

            // Уведомляем всех участников о добавлении новых пользователей
            notifyParticipantsAboutChatUpdate(updatedChat, "PARTICIPANTS_ADDED");

            return convertToDto(updatedChat);
        }

        return convertToDto(chat);
    }

    /**
     * Удалить участника из группового чата
     */
    public ChatDto removeParticipant(Long chatId, Long currentUserId, Long participantId) {
        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new RuntimeException("Чат не найден"));

        // Проверяем, что это групповой чат
        if (chat.getChatType() != Chat.ChatType.GROUP) {
            throw new IllegalArgumentException("Удалять участников можно только из групповых чатов");
        }

        // Проверяем, что текущий пользователь является создателем чата
        boolean isCreator = chat.getCreatedBy().getId().equals(currentUserId);
        if (!isCreator) {
            throw new IllegalArgumentException("Только создатель чата может удалять участников");
        }

        // Проверяем, что удаляемый пользователь не создатель чата
        if (chat.getCreatedBy().getId().equals(participantId)) {
            throw new IllegalArgumentException("Нельзя удалить создателя чата");
        }

        User participantToRemove = userRepository.findById(participantId)
            .orElseThrow(() -> new RuntimeException("Удаляемый пользователь не найден"));

        // Удаляем участника из чата
        Set<User> participants = new HashSet<>(chat.getParticipants());
        if (participants.remove(participantToRemove)) {
            chat.setParticipants(participants);
            Chat updatedChat = chatRepository.save(chat);

            // Отправляем системное сообщение
            String message = String.format("Пользователь %s был удален из чата",
                participantToRemove.getUsername());
            sendSystemMessage(updatedChat, message);

            // Уведомляем оставшихся участников об удалении
            notifyParticipantsAboutChatUpdate(updatedChat, "PARTICIPANT_REMOVED");

            // Отдельно уведомляем удаленного пользователя
            notifyUserAboutChatUpdate(participantId, chatId, "REMOVED_FROM_CHAT");

            return convertToDto(updatedChat);
        }

        return convertToDto(chat);
    }

    /**
     * Покинуть чат
     */
    public void leaveChat(Long chatId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new RuntimeException("Чат не найден"));

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        // Для приватных чатов мы просто удаляем пользователя из списка участников
        if (chat.getChatType() == Chat.ChatType.PRIVATE) {
            Set<User> participants = new HashSet<>(chat.getParticipants());
            participants.remove(user);
            chat.setParticipants(participants);
            chatRepository.save(chat);

            // Уведомляем другого участника
            chat.getParticipants().forEach(participant -> {
                if (!participant.getId().equals(userId)) {
                    notifyUserAboutChatUpdate(participant.getId(), chatId, "PARTICIPANT_LEFT");
                }
            });

            return;
        }

        // Для групповых чатов проверяем, является ли пользователь создателем
        if (chat.getCreatedBy().getId().equals(userId)) {
            // Если создатель покидает чат, назначаем нового создателя (если есть другие участники)
            Set<User> participants = new HashSet<>(chat.getParticipants());
            participants.remove(user);

            if (!participants.isEmpty()) {
                User newCreator = participants.iterator().next();
                chat.setCreatedBy(newCreator);
                chat.setParticipants(participants);

                Chat updatedChat = chatRepository.save(chat);

                // Системное сообщение о смене создателя
                String message = String.format("%s покинул(а) чат. %s назначен(а) новым администратором.",
                    user.getUsername(), newCreator.getUsername());
                sendSystemMessage(updatedChat, message);

                // Уведомляем всех участников
                notifyParticipantsAboutChatUpdate(updatedChat, "CREATOR_CHANGED");
            } else {
                // Если больше нет участников, удаляем чат
                chatRepository.delete(chat);
            }
        } else {
            // Если обычный участник покидает чат
            Set<User> participants = new HashSet<>(chat.getParticipants());
            participants.remove(user);
            Chat updatedChat = chatRepository.save(chat);

            // Системное сообщение
            String message = String.format("%s покинул(а) чат", user.getUsername());
            sendSystemMessage(updatedChat, message);

            // Уведомляем оставшихся участников
            notifyParticipantsAboutChatUpdate(updatedChat, "PARTICIPANT_LEFT");
        }
    }

    /**
     * Получить информацию о чате
     */
    @Transactional(readOnly = true)
    public ChatDto getChatInfo(Long chatId, Long userId) {
        // Сначала проверяем, является ли пользователь участником чата
        if (!chatRepository.isUserParticipant(chatId, userId)) {
            throw new IllegalArgumentException("У вас нет доступа к этому чату");
        }

        // Загружаем чат с участниками
        Chat chat = chatRepository.findByIdWithParticipants(chatId)
            .orElseThrow(() -> new RuntimeException("Чат не найден"));

        return convertToDto(chat);
    }

    /**
     * Поиск чатов по названию
     */
    @Transactional(readOnly = true)
    public List<ChatDto> searchChats(String query, Long userId) {
        // Для безопасности ищем только в чатах, где пользователь является участником
        List<Chat> userChats = chatRepository.findChatsByUserId(userId);

        return userChats.stream()
            .filter(chat -> chat.getChatName().toLowerCase().contains(query.toLowerCase()))
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * Получить список ID участников чата (для WebSocket сервера)
     */
    @Transactional(readOnly = true)
    public List<Long> getChatParticipantIds(Long chatId, Long requestingUserId) {
        // Проверяем существование чата
        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new IllegalArgumentException("Чат с ID " + chatId + " не найден"));

        // Проверяем, что запрашивающий пользователь является участником чата
        boolean isParticipant = chat.getParticipants().stream()
            .anyMatch(user -> user.getId().equals(requestingUserId));

        if (!isParticipant) {
            throw new IllegalArgumentException("Пользователь не является участником данного чата");
        }

        // Возвращаем список ID всех участников чата
        return chat.getParticipants().stream()
            .map(User::getId)
            .collect(Collectors.toList());
    }

    /**
     * Получить список ID участников чата (для внутренних сервисов без проверки авторизации)
     */
    @Transactional(readOnly = true)
    public List<Long> getChatParticipantIdsInternal(Long chatId) {
        // Проверяем существование чата
        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new IllegalArgumentException("Чат с ID " + chatId + " не найден"));

        // Возвращаем список ID всех участников чата без проверки авторизации
        List<Long> participantIds = chat.getParticipants().stream()
            .map(User::getId)
            .collect(Collectors.toList());

        log.info("[INTERNAL] Returning {} participants for chat {}: {}",
            participantIds.size(), chatId, participantIds);

        return participantIds;
    }

    /**
     * Удалить чат (только создатель может удалить)
     */
    public void deleteChat(Long chatId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Чат не найден"));
        if (chat.getCreatedBy() == null || !chat.getCreatedBy().getId().equals(userId)) {
            throw new IllegalArgumentException("Только создатель чата может удалить чат");
        }
        chatRepository.delete(chat);
    }

    /**
     * Отправить системное сообщение в чат
     */
    private void sendSystemMessage(Chat chat, String content) {
        Message message = new Message();
        message.setChat(chat);
        message.setContent(content);
        message.setMessageType(Message.MessageType.SYSTEM);
        message.setCreatedAt(LocalDateTime.now());
        // Для системных сообщений устанавливаем sender_id равным создателю чата
        if (chat.getCreatedBy() != null) {
            message.setSender(chat.getCreatedBy());
        }

        messageRepository.save(message);

        // Обновляем время последнего сообщения в чате
        chat.setLastMessageAt(LocalDateTime.now());
        chatRepository.save(chat);
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

        // Конвертируем создателя
        if (chat.getCreatedBy() != null) {
            dto.setCreatedBy(userService.convertToDto(chat.getCreatedBy()));
        }

        // Конвертируем участников
        if (chat.getParticipants() != null) {
            dto.setParticipants(chat.getParticipants().stream()
                .map(userService::convertToDto)
                .collect(Collectors.toList()));
        }

        // Получаем последнее сообщение
        if (chat.getMessages() != null && !chat.getMessages().isEmpty()) {
            Message lastMessage = chat.getMessages().stream()
                .max(Comparator.comparing(Message::getCreatedAt))
                .orElse(null);

            if (lastMessage != null) {
                dto.setLastMessage(messageService.convertToDto(lastMessage));
            }
        }

        return dto;
    }

    /**
     * Конвертировать Chat в ChatDto (оптимизированный вариант)
     */
    private ChatDto convertToDtoOptimized(Chat chat, Message lastMessage, Long userId, Integer unreadCount) {
        ChatDto dto = new ChatDto();
        dto.setId(chat.getId());
        dto.setChatName(chat.getChatName());
        dto.setChatType(chat.getChatType());
        dto.setChatDescription(chat.getChatDescription());

        // ИСПРАВЛЕНО: Для личных чатов используем аватарку собеседника
        if (chat.getChatType() == Chat.ChatType.PRIVATE && chat.getParticipants() != null && userId != null) {
            // Находим собеседника (не текущего пользователя)
            User otherParticipant = chat.getParticipants().stream()
                .filter(participant -> !participant.getId().equals(userId))
                .findFirst()
                .orElse(null);

            if (otherParticipant != null && otherParticipant.getProfilePictureUrl() != null) {
                // Устанавливаем аватарку собеседника
                dto.setChatAvatarUrl(otherParticipant.getProfilePictureUrl());
                log.debug("Установлена аватарка собеседника для чата {}: {}", chat.getId(), otherParticipant.getProfilePictureUrl());
            } else {
                dto.setChatAvatarUrl(chat.getChatAvatarUrl());
                log.debug("Используется аватарка чата по умолчанию для чата {}: {}", chat.getId(), chat.getChatAvatarUrl());
            }
        } else {
            // Для групповых чатов используем аватарку группы
            dto.setChatAvatarUrl(chat.getChatAvatarUrl());
            log.debug("Установлена аватарка группы для чата {}: {}", chat.getId(), chat.getChatAvatarUrl());
        }

        dto.setCreatedAt(chat.getCreatedAt());
        dto.setLastMessageAt(chat.getLastMessageAt());

        // Конвертируем создателя
        if (chat.getCreatedBy() != null) {
            dto.setCreatedBy(userService.convertToDto(chat.getCreatedBy()));
        }

        // Конвертируем участников
        if (chat.getParticipants() != null) {
            dto.setParticipants(chat.getParticipants().stream()
                .map(userService::convertToDto)
                .collect(Collectors.toList()));
        }

        // Устанавливаем последнее сообщение, если есть
        if (lastMessage != null) {
            dto.setLastMessage(messageService.convertToDto(lastMessage));
        }

        // ИСПРАВЛЕНО: Подсчитываем непрочитанные сообщения для текущего пользователя с улучшенной обработкой ошибок
        dto.setUnreadCount(unreadCount != null ? unreadCount : 0);

        return dto;
    }

    /**
     * Получить последние сообщения для списка чатов
     */
    private Map<Long, Message> getLastMessagesForChats(List<Chat> chats) {
        List<Long> chatIds = chats.stream()
            .map(Chat::getId)
            .collect(Collectors.toList());

        // Получаем последние сообщения для чатов одним запросом
        List<Message> messages = messageRepository.findLastMessagesByChatIds(chatIds);


        Map<Long, Message> lastMessagesMap = new HashMap<>();
        for (Message message : messages) {
            lastMessagesMap.put(message.getChat().getId(), message);
        }

        return lastMessagesMap;
    }

    /**
     * Получить счетчики непрочитанных сообщений для списка чатов (оптимизированный запрос)
     */
    private Map<Long, Integer> getUnreadCountsForChats(List<Chat> chats, Long userId) {
        List<Long> chatIds = chats.stream()
            .map(Chat::getId)
            .collect(Collectors.toList());

        log.info("Запрашиваем счетчики непрочитанных для {} чатов: {}", chatIds.size(), chatIds);

        // Получаем счетчики непрочитанных сообщений для чатов одним запросом
        List<Object[]> results = messageReadStatusRepository.countUnreadMessagesForChats(chatIds, userId);

        log.info("Получено {} результатов от batch-запроса", results.size());

        Map<Long, Integer> unreadCountsMap = new HashMap<>();
        for (Object[] result : results) {
            Long chatId = ((Number) result[0]).longValue();
            Long unreadCount = ((Number) result[1]).longValue();

            unreadCountsMap.put(chatId, unreadCount.intValue());
            log.debug("Чат {}: {} непрочитанных сообщений", chatId, unreadCount);
        }

        // Заполняем нулями для чатов без непрочитанных сообщений
        for (Chat chat : chats) {
            unreadCountsMap.putIfAbsent(chat.getId(), 0);
        }

        log.info("Итого счетчиков в Map: {}", unreadCountsMap.size());

        return unreadCountsMap;
    }

    /**
     * Уведомить всех участников чата об обновлении
     */
    private void notifyParticipantsAboutChatUpdate(Chat chat, String eventType) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("eventType", eventType);
        notification.put("chatId", chat.getId());
        notification.put("timestamp", System.currentTimeMillis());

        // Отправляем уведомление каждому участнику чата
        chat.getParticipants().forEach(user -> {

            kafkaTemplate.send("user-notifications", user.getId().toString(), notification);
        });
    }

    /**
     * Уведомить конкретного пользователя об обновлении чата
     */
    private void notifyUserAboutChatUpdate(Long userId, Long chatId, String eventType) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("eventType", eventType);
        notification.put("chatId", chatId);
        notification.put("timestamp", System.currentTimeMillis());


        kafkaTemplate.send("user-notifications", userId.toString(), notification);
    }

    // Методы для тестирования

    /**
     * Найти чат по ID (для тестов)
     */
    public Optional<Chat> findChatById(Long chatId) {
        return chatRepository.findById(chatId);
    }

    /**
     * Создать чат (для тестов)
     */
    public Chat createChat(Chat chat) {
        return chatRepository.save(chat);
    }

    /**
     * Добавить участника в чат (для тестов)
     */
    public void addParticipant(Long chatId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new RuntimeException("Чат не найден"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        Set<User> participants = new HashSet<>(chat.getParticipants());
        participants.add(user);
        chat.setParticipants(participants);
        chatRepository.save(chat);
    }

    /**
     * Получить участников чата
     */
    public Set<User> getChatParticipants(Long chatId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new RuntimeException("Чат не найден"));
        // Проверяем, что пользователь участник
        boolean isParticipant = chat.getParticipants().stream()
            .anyMatch(u -> u.getId().equals(userId));
        if (!isParticipant) {
            throw new IllegalArgumentException("Нет доступа к участникам чата");
        }
        return chat.getParticipants();
    }
}
