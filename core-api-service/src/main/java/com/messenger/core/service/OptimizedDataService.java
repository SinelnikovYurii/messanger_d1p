package com.messenger.core.service;

import com.messenger.core.dto.ChatDto;
import com.messenger.core.dto.MessageDto;
import com.messenger.core.model.Chat;
import com.messenger.core.model.Message;
import com.messenger.core.repository.ChatRepository;
import com.messenger.core.repository.MessageRepository;
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
    private final UserService userService;
    private final MessageService messageService;

    /**
     * Оптимизированное получение чатов пользователя с минимальным количеством запросов
     */
    @Transactional(readOnly = true)
    public List<ChatDto> getOptimizedUserChats(Long userId) {
        log.debug("Getting optimized user chats for user {}", userId);

        // 1. Получаем чаты с предварительно загруженными участниками и создателями
        List<Chat> chats = chatRepository.findChatsByUserIdWithParticipants(userId);
        log.debug("Found {} chats for user {}", chats.size(), userId);

        if (chats.isEmpty()) {
            return List.of();
        }

        // 2. Получаем ID всех чатов
        List<Long> chatIds = chats.stream()
            .map(Chat::getId)
            .collect(Collectors.toList());

        // 3. Batch загружаем последние сообщения для всех чатов одним запросом
        List<Message> lastMessages = messageRepository.findLastMessagesByChatIds(chatIds);
        log.debug("Found {} last messages for {} chats", lastMessages.size(), chatIds.size());

        // 4. Создаем карту для быстрого доступа к последним сообщениям
        Map<Long, Message> lastMessageMap = lastMessages.stream()
            .collect(Collectors.toMap(
                msg -> msg.getChat().getId(),
                msg -> msg,
                (existing, replacement) -> existing // В случае дубликатов оставляем существующий
            ));

        // 5. Конвертируем в DTO с минимальным количеством дополнительных запросов
        return chats.stream()
            .map(chat -> convertChatToDtoOptimized(chat, lastMessageMap.get(chat.getId())))
            .collect(Collectors.toList());
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
     * Оптимизированная конвертация Chat в ChatDto
     */
    private ChatDto convertChatToDtoOptimized(Chat chat, Message lastMessage) {
        ChatDto dto = new ChatDto();
        dto.setId(chat.getId());
        dto.setChatName(chat.getChatName());
        dto.setChatType(chat.getChatType());
        dto.setChatDescription(chat.getChatDescription());
        dto.setChatAvatarUrl(chat.getChatAvatarUrl());
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
