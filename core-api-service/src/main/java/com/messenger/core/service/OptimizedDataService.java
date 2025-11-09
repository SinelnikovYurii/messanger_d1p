package com.messenger.core.service;

import com.messenger.core.dto.ChatDto;
import com.messenger.core.dto.MessageDto;
import com.messenger.core.model.Chat;
import com.messenger.core.model.Message;
import com.messenger.core.model.User;
import com.messenger.core.repository.ChatRepository;
import com.messenger.core.repository.MessageRepository;
import com.messenger.core.repository.MessageReadStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OptimizedDataService {

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final MessageReadStatusRepository messageReadStatusRepository;
    private final UserService userService;
    private final MessageService messageService;

    /**
     * Оптимизированное получение чатов пользователя с минимальным количеством запросов
     */
    @Transactional(readOnly = true)
    public List<ChatDto> getOptimizedUserChats(Long userId) {
        log.info("=== НАЧАЛО getOptimizedUserChats для пользователя {} ===", userId);

        // 1. Получаем чаты пользователя
        List<Chat> chats = chatRepository.findChatsByUserIdWithParticipants(userId);
        log.info("Загружено {} чатов для пользователя {}", chats.size(), userId);

        if (chats.isEmpty()) {
            return List.of();
        }

        // 2. Получаем ID всех чатов
        List<Long> chatIds = chats.stream()
            .map(Chat::getId)
            .collect(Collectors.toList());

        // ИСПРАВЛЕНО: Batch-загружаем всех участников для всех чатов одним запросом
        List<Chat> chatsWithParticipants = chatRepository.findChatsByIdsWithParticipants(chatIds);
        log.info("Загружены участники для {} чатов", chatsWithParticipants.size());

        // Создаем карту для быстрого доступа к чатам с участниками
        Map<Long, Chat> chatMap = chatsWithParticipants.stream()
            .collect(Collectors.toMap(Chat::getId, chat -> chat));

        // 3. Batch загружаем последние сообщения для всех чатов одним запросом
        List<Message> lastMessages = messageRepository.findLastMessagesByChatIds(chatIds);
        log.info("Загружены последние сообщения для {} чатов", lastMessages.size());

        // 4. Создаем карту для быстрого доступа к последним сообщениям
        Map<Long, Message> lastMessageMap = lastMessages.stream()
            .collect(Collectors.toMap(
                msg -> msg.getChat().getId(),
                msg -> msg,
                (existing, replacement) -> existing // В случае дубликатов оставляем существующий
            ));

        // 5. ИСПРАВЛЕНО: Batch-загрузка счетчиков непрочитанных сообщений одним запросом
        Map<Long, Integer> unreadCounts = getUnreadCountsForChats(chatIds, userId);
        log.info("Загружены счетчики непрочитанных для {} чатов", unreadCounts.size());

        // 6. Конвертируем в DTO с минимальным количеством дополнительных запросов
        // ИСПРАВЛЕНО: Используем чаты с загруженными участниками из chatMap
        List<ChatDto> result = chatsWithParticipants.stream()
            .map(chat -> convertChatToDtoOptimized(chat, lastMessageMap.get(chat.getId()), userId, unreadCounts.get(chat.getId())))
            .collect(Collectors.toList());

        log.info("=== ЗАВЕРШЕНО getOptimizedUserChats: возвращено {} DTO ===", result.size());

        // Логируем детали первого чата для отладки
        if (!result.isEmpty()) {
            ChatDto firstChat = result.get(0);
            log.info("Пример первого чата: ID={}, Name={}, Type={}, AvatarUrl={}, UnreadCount={}",
                firstChat.getId(), firstChat.getChatName(), firstChat.getChatType(),
                firstChat.getChatAvatarUrl(), firstChat.getUnreadCount());
        }

        return result;
    }

    /**
     * Оптимизированное получение сообщений чата
     */
    @Transactional(readOnly = true)
    public List<MessageDto> getOptimizedChatMessages(Long chatId, Long userId, int page, int size) {
        log.debug("Getting optimized messages for chat {} (page {}, size {})", chatId, page, size);

        // Проверяем доступ пользователя к чату
        if (!chatRepository.isUserParticipant(chatId, userId)) {
            throw new IllegalArgumentException("У вас нет доступа к этому чату");
        }

        Pageable pageable = PageRequest.of(page, size);

        // Используем оптимизированный запрос с предварительной загрузкой отправителей
        List<Message> messages = messageRepository.findByChatIdOrderByCreatedAtDescWithSender(chatId, pageable);
        log.debug("Found {} messages for chat {}", messages.size(), chatId);

        // Конвертируем с информацией о прочтении для текущего пользователя
        return messages.stream()
            .map(message -> messageService.convertToDto(message, userId, false))
            .collect(Collectors.toList());
    }

    /**
     * ИСПРАВЛЕНО: Получить счетчики непрочитанных сообщений для списка чатов (оптимизированный запрос)
     */
    private Map<Long, Integer> getUnreadCountsForChats(List<Long> chatIds, Long userId) {
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
        for (Long chatId : chatIds) {
            unreadCountsMap.putIfAbsent(chatId, 0);
        }

        log.info("Итого счетчиков в Map: {}", unreadCountsMap.size());

        return unreadCountsMap;
    }

    /**
     * ИСПРАВЛЕНО: Оптимизированная конвертация Chat в ChatDto
     */
    private ChatDto convertChatToDtoOptimized(Chat chat, Message lastMessage, Long userId, Integer unreadCount) {
        ChatDto dto = new ChatDto();
        dto.setId(chat.getId());
        dto.setChatName(chat.getChatName());
        dto.setChatType(chat.getChatType());
        dto.setChatDescription(chat.getChatDescription());

        log.info("Конвертируем чат ID={}, Type={}, Participants={}",
            chat.getId(), chat.getChatType(), chat.getParticipants() != null ? chat.getParticipants().size() : 0);

        // ИСПРАВЛЕНО: Для личных чатов используем аватарку собеседника
        if (chat.getChatType() == Chat.ChatType.PRIVATE && chat.getParticipants() != null && userId != null) {
            log.info("Чат {} - ПРИВАТНЫЙ, ищем собеседника для userId={}", chat.getId(), userId);

            // Находим собеседника (не текущего пользователя)
            User otherParticipant = chat.getParticipants().stream()
                .filter(participant -> !participant.getId().equals(userId))
                .findFirst()
                .orElse(null);

            if (otherParticipant != null) {
                log.info("Найден собеседник: ID={}, Username={}, ProfilePictureUrl={}",
                    otherParticipant.getId(), otherParticipant.getUsername(), otherParticipant.getProfilePictureUrl());

                if (otherParticipant.getProfilePictureUrl() != null) {
                    // Устанавливаем аватарку собеседника
                    dto.setChatAvatarUrl(otherParticipant.getProfilePictureUrl());
                    log.info("✅ Установлена аватарка собеседника для чата {}: {}", chat.getId(), otherParticipant.getProfilePictureUrl());
                } else {
                    dto.setChatAvatarUrl(chat.getChatAvatarUrl());
                    log.info("⚠️ У собеседника нет аватарки, используем аватарку чата: {}", chat.getChatAvatarUrl());
                }
            } else {
                dto.setChatAvatarUrl(chat.getChatAvatarUrl());
                log.warn("❌ Не найден собеседник для приватного чата {}", chat.getId());
            }
        } else {
            // Для групповых чатов используем аватарку группы
            dto.setChatAvatarUrl(chat.getChatAvatarUrl());
            log.info("Установлена аватарка группы для чата {}: {}", chat.getId(), chat.getChatAvatarUrl());
        }

        dto.setCreatedAt(chat.getCreatedAt());
        dto.setLastMessageAt(chat.getLastMessageAt());

        // Конвертируем создателя (уже загружен благодаря JOIN FETCH)
        if (chat.getCreatedBy() != null) {
            dto.setCreatedBy(userService.convertToDto(chat.getCreatedBy()));
        }

        // Конвертируем участников (уже загружены благодаря JOIN FETCH)
        if (chat.getParticipants() != null) {
            dto.setParticipants(chat.getParticipants().stream()
                .map(userService::convertToDto)
                .collect(Collectors.toList()));
        }

        // Устанавливаем последнее сообщение (уже загружено batch запросом)
        if (lastMessage != null) {
            dto.setLastMessage(messageService.convertToDto(lastMessage));
        }

        // ИСПРАВЛЕНО: Устанавливаем количество непрочитанных сообщений
        dto.setUnreadCount(unreadCount != null ? unreadCount : 0);

        return dto;
    }

    /**
     * Получить статистику производительности для чата
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getChatPerformanceStats(Long chatId) {
        Map<String, Object> stats = new HashMap<>();

        long messageCount = messageRepository.countMessagesByChatId(chatId);
        stats.put("messageCount", messageCount);

        Chat chat = chatRepository.findById(chatId).orElse(null);
        if (chat != null) {
            stats.put("participantCount", chat.getParticipants() != null ? chat.getParticipants().size() : 0);
            stats.put("chatType", chat.getChatType());
            stats.put("createdAt", chat.getCreatedAt());
            stats.put("lastMessageAt", chat.getLastMessageAt());
        }

        return stats;
    }
}
