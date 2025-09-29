package com.messenger.core.controller;

import com.messenger.core.dto.ChatDto;
import com.messenger.core.service.ChatService;
import com.messenger.core.config.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Получить все чаты пользователя
     */
    @GetMapping
    public ResponseEntity<List<ChatDto>> getUserChats(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<ChatDto> chats = chatService.getUserChats(userId);
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
