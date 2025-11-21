package com.messenger.core.controller;

import com.messenger.core.dto.ChatDto;
import com.messenger.core.service.ChatService;
import com.messenger.core.service.OptimizedDataService;
import com.messenger.core.config.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final OptimizedDataService optimizedDataService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Получить все чаты пользователя (оптимизированная версия)
     */
    @GetMapping
    public ResponseEntity<List<ChatDto>> getUserChats(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        // Используем оптимизированный сервис для минимизации запросов к БД
        List<ChatDto> chats = optimizedDataService.getOptimizedUserChats(userId);
        return ResponseEntity.ok(chats);
    }

    /**
     * Создать приватный чат с другим пользователем
     */
    @PostMapping("/private")
    public ResponseEntity<ChatDto> createPrivateChat(
            @RequestBody ChatDto.CreatePrivateChatRequest request,
            HttpServletRequest httpRequest) {
        Long currentUserId = getCurrentUserId(httpRequest);
        ChatDto chat = chatService.createPrivateChat(currentUserId, request.getParticipantId());
        return ResponseEntity.ok(chat);
    }

    /**
     * Создать групповой чат
     */
    @PostMapping("/group")
    public ResponseEntity<ChatDto> createGroupChat(
            @RequestBody ChatDto.CreateChatRequest request,
            HttpServletRequest httpRequest) {
        Long currentUserId = getCurrentUserId(httpRequest);
        if (request.getChatName() == null || request.getChatName().trim().isEmpty()) {
            throw new IllegalArgumentException("Имя чата не может быть пустым");
        }
        ChatDto chat = chatService.createGroupChat(currentUserId, request);
        return ResponseEntity.ok(chat);
    }

    /**
     * Добавить участников в групповой чат
     */
    @PostMapping("/{chatId}/participants")
    public ResponseEntity<ChatDto> addParticipants(
            @PathVariable Long chatId,
            @RequestBody List<Long> userIds,
            HttpServletRequest request) {
        Long currentUserId = getCurrentUserId(request);
        ChatDto chat = chatService.addParticipants(chatId, currentUserId, userIds);
        return ResponseEntity.ok(chat);
    }

    /**
     * Удалить участника из группового чата
     */
    @DeleteMapping("/{chatId}/participants/{participantId}")
    public ResponseEntity<ChatDto> removeParticipant(
            @PathVariable Long chatId,
            @PathVariable Long participantId,
            HttpServletRequest request) {
        Long currentUserId = getCurrentUserId(request);
        ChatDto chat = chatService.removeParticipant(chatId, currentUserId, participantId);
        return ResponseEntity.ok(chat);
    }

    /**
     * Покинуть чат
     */
    @DeleteMapping("/{chatId}/leave")
    public ResponseEntity<Void> leaveChat(
            @PathVariable Long chatId,
            HttpServletRequest request) {
        Long currentUserId = getCurrentUserId(request);
        chatService.leaveChat(chatId, currentUserId);
        return ResponseEntity.ok().build();
    }

    /**
     * Получить информацию о чате
     */
    @GetMapping("/{chatId}")
    public ResponseEntity<ChatDto> getChatInfo(
            @PathVariable Long chatId,
            HttpServletRequest request) {
        Long currentUserId = getCurrentUserId(request);
        ChatDto chat = chatService.getChatInfo(chatId, currentUserId);
        return ResponseEntity.ok(chat);
    }

    /**
     * Поиск чатов по названию
     */
    @GetMapping("/search")
    public ResponseEntity<List<ChatDto>> searchChats(
            @RequestParam String query,
            HttpServletRequest request) {
        Long currentUserId = getCurrentUserId(request);
        List<ChatDto> chats = chatService.searchChats(query, currentUserId);
        return ResponseEntity.ok(chats);
    }

    /**
     * Получить список ID участников чата (для WebSocket сервера)
     */
    @GetMapping("/{chatId}/participants")
    public ResponseEntity<List<Long>> getChatParticipants(
            @PathVariable Long chatId,
            HttpServletRequest request) {

        // Проверяем, является ли запрос от внутреннего сервиса
        String internalService = request.getHeader("X-Internal-Service");
        String serviceAuth = request.getHeader("X-Service-Auth");

        if ("websocket-server".equals(internalService) && "internal-service-key".equals(serviceAuth)) {
            // Запрос от внутреннего сервиса - не требуем авторизации пользователя
            log.info("[INTERNAL] Processing internal request from {} for chat {}", internalService, chatId);
            List<Long> participantIds = chatService.getChatParticipantIdsInternal(chatId);
            return ResponseEntity.ok(participantIds);
        } else {
            // Обычный пользовательский запрос - требуем авторизации
            Long currentUserId = getCurrentUserId(request);
            List<Long> participantIds = chatService.getChatParticipantIds(chatId, currentUserId);
            return ResponseEntity.ok(participantIds);
        }
    }

    /**
     * Получить ID текущего пользователя из заголовков Gateway
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        // Сначала пробуем получить из заголовков Gateway
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isEmpty()) {
            try {
                return Long.parseLong(userIdHeader);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Неверный формат ID пользователя в заголовке: " + userIdHeader);
            }
        }

        // Если заголовка нет, пробуем через JWT фильтр
        Long userId = null;
        try {
            userId = jwtAuthenticationFilter.getUserIdFromRequest(request);
        } catch (Exception e) {
            throw new IllegalArgumentException("Ошибка авторизации: " + e.getMessage(), e);
        }

        if (userId == null) {
            throw new IllegalArgumentException("Пользователь не аутентифицирован - отсутствуют данные авторизации");
        }

        return userId;
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException ex) {
        if ("Chat not found".equals(ex.getMessage())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Chat not found");
        }
        if (ex instanceof IllegalArgumentException) {
            String msg = ex.getMessage();
            if (msg != null && (msg.contains("Ошибка авторизации") || msg.contains("Пользователь не аутентифицирован"))) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(msg);
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(msg);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
    }
}
