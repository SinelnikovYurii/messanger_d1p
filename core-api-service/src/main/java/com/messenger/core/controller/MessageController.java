package com.messenger.core.controller;

import com.messenger.core.dto.MessageDto;
import com.messenger.core.dto.request.SendMessageRequest;
import com.messenger.core.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/chat/{chatId}")
    public ResponseEntity<Page<MessageDto>> getChatMessages(
            @PathVariable Long chatId,
            @RequestParam Long userId,
            Pageable pageable) {

        Page<MessageDto> messages = messageService.getChatMessages(chatId, userId, pageable);
        return ResponseEntity.ok(messages);
    }

    @PostMapping
    public ResponseEntity<MessageDto> sendMessage(
            @Valid @RequestBody SendMessageRequest request,
            @RequestParam Long senderId) {

        MessageDto message = messageService.sendMessage(request, senderId);
        return ResponseEntity.ok(message);
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<MessageDto> editMessage(
            @PathVariable Long messageId,
            @RequestBody String newContent,
            @RequestParam Long userId) {

        MessageDto message = messageService.editMessage(messageId, newContent, userId);
        return ResponseEntity.ok(message);
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long messageId,
            @RequestParam Long userId) {

        messageService.deleteMessage(messageId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/chat/{chatId}/mark-read")
    public ResponseEntity<Void> markMessagesAsRead(
            @PathVariable Long chatId,
            @RequestParam Long userId) {

        messageService.markMessagesAsRead(chatId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/chat/{chatId}/search")
    public ResponseEntity<List<MessageDto>> searchMessages(
            @PathVariable Long chatId,
            @RequestParam String query,
            @RequestParam Long userId) {

        List<MessageDto> messages = messageService.searchMessagesInChat(chatId, query, userId);
        return ResponseEntity.ok(messages);
    }
}
