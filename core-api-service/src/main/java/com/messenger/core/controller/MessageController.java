package com.messenger.core.controller;

import com.messenger.core.dto.MessageDto;
import com.messenger.core.service.MessageService;
import com.messenger.core.service.OptimizedDataService;
import com.messenger.core.config.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final OptimizedDataService optimizedDataService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Отправить сообщение в чат
     */
    @PostMapping
    public ResponseEntity<MessageDto> sendMessage(
            @RequestBody MessageDto.SendMessageRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId(httpRequest);
        MessageDto message = messageService.sendMessage(userId, request);
        return ResponseEntity.ok(message);
    }

    /**
     * Получить сообщения чата (оптимизированная версия)
     */
    @GetMapping("/chat/{chatId}")
    public ResponseEntity<List<MessageDto>> getChatMessages(
            @PathVariable Long chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        // Используем оптимизированный сервис для минимизации запросов к БД
        List<MessageDto> messages = optimizedDataService.getOptimizedChatMessages(chatId, userId, page, size);
        return ResponseEntity.ok(messages);
    }

    /**
     * Редактировать сообщение
     */
    @PutMapping("/{messageId}")
    public ResponseEntity<MessageDto> editMessage(
            @PathVariable Long messageId,
            @RequestBody MessageDto.EditMessageRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId(httpRequest);
        MessageDto message = messageService.editMessage(messageId, userId, request);
        return ResponseEntity.ok(message);
    }

    /**
     * Удалить сообщение
     */
    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long messageId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        messageService.deleteMessage(messageId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Поиск сообщений в чате
     */
    @GetMapping("/chat/{chatId}/search")
    public ResponseEntity<List<MessageDto>> searchMessages(
            @PathVariable Long chatId,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<MessageDto> messages = messageService.searchMessagesInChat(chatId, userId, query, page, size);
        return ResponseEntity.ok(messages);
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
                throw new RuntimeException("Неверный формат ID пользователя в заголовке: " + userIdHeader);
            }
        }

        // Если заголовка нет, пробуем через JWT фильтр (fallback)
        Long userId = null;
        try {
            userId = jwtAuthenticationFilter.getUserIdFromRequest(request);
        } catch (Exception e) {
            // Игнорируем ошибку JWT фильтра, если есть заголовки Gateway
        }

        if (userId == null) {
            throw new RuntimeException("Пользователь не аутентифицирован - отсутствуют данные авторизации");
        }

        return userId;
    }
}
