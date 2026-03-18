package com.messenger.core.controller;

import com.messenger.core.dto.MessageDto;
import com.messenger.core.dto.MessageReadStatusDto;
import com.messenger.core.service.message.MessageService;
import com.messenger.core.service.user.UserContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final UserContextResolver userContextResolver;

    @Value("${app.messages.default-page-size}")
    private int defaultPageSize;

    @Value("${app.messages.default-search-page-size}")
    private int defaultSearchPageSize;

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
     * Получить сообщения чата
     */
    @GetMapping("/chat/{chatId}")
    public ResponseEntity<List<MessageDto>> getChatMessages(
            @PathVariable Long chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<MessageDto> messages = messageService.getChatMessages(
                chatId, userId, page, size != null ? size : defaultPageSize);
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
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<MessageDto> messages = messageService.searchMessagesInChat(
                chatId, userId, query, page, size != null ? size : defaultSearchPageSize);
        return ResponseEntity.ok(messages);
    }

    /**
     * Отметить сообщения как прочитанные
     */
    @PostMapping("/read")
    public ResponseEntity<Void> markMessagesAsRead(
            @RequestBody List<Long> messageIds,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        messageService.markMessagesAsRead(userId, messageIds);
        return ResponseEntity.ok().build();
    }

    /**
     * Отметить все сообщения в чате как прочитанные
     */
    @PostMapping("/chat/{chatId}/read-all")
    public ResponseEntity<Void> markAllChatMessagesAsRead(
            @PathVariable Long chatId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        messageService.markAllChatMessagesAsRead(userId, chatId);
        return ResponseEntity.ok().build();
    }

    /**
     * Получить количество непрочитанных сообщений в чате
     */
    @GetMapping("/chat/{chatId}/unread-count")
    public ResponseEntity<Long> getUnreadMessagesCount(
            @PathVariable Long chatId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        long count = messageService.getUnreadMessagesCount(userId, chatId);
        return ResponseEntity.ok(count);
    }

    /**
     * Получить статусы прочтения для конкретного сообщения
     */
    @GetMapping("/{messageId}/read-status")
    public ResponseEntity<List<MessageReadStatusDto>> getMessageReadStatuses(
            @PathVariable Long messageId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<MessageReadStatusDto> statuses = messageService.getMessageReadStatuses(messageId, userId);
        return ResponseEntity.ok(statuses);
    }

    /**
     * Получить ID текущего пользователя из заголовков Gateway
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        return userContextResolver.resolveUserId(request);
    }
}
