package com.messenger.core.controller;

import com.messenger.core.dto.ChatDto;
import com.messenger.core.dto.request.CreateChatRequest;
import com.messenger.core.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public ResponseEntity<List<ChatDto>> getUserChats(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<ChatDto> chats = chatService.getUserChats(userId);
        return ResponseEntity.ok(chats);
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<ChatDto> getChatById(
            @PathVariable Long chatId,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        return chatService.getChatById(chatId, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ChatDto> createChat(
            @Valid @RequestBody CreateChatRequest createRequest,
            HttpServletRequest request) {

        Long creatorId = (Long) request.getAttribute("userId");
        ChatDto chat = chatService.createChat(createRequest, creatorId);
        return ResponseEntity.ok(chat);
    }

    @PostMapping("/private")
    public ResponseEntity<ChatDto> createPrivateChat(
            @RequestParam Long otherUserId,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        ChatDto chat = chatService.createPrivateChat(userId, otherUserId);
        return ResponseEntity.ok(chat);
    }

    @PostMapping("/{chatId}/participants")
    public ResponseEntity<ChatDto> addParticipant(
            @PathVariable Long chatId,
            @RequestParam Long newParticipantId,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        ChatDto chat = chatService.addParticipant(chatId, userId, newParticipantId);
        return ResponseEntity.ok(chat);
    }

    @DeleteMapping("/{chatId}/participants/{participantId}")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable Long chatId,
            @PathVariable Long participantId,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        chatService.removeParticipant(chatId, userId, participantId);
        return ResponseEntity.ok().build();
    }
}
