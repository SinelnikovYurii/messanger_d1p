package com.messenger.gateway.controller;

import com.messenger.gateway.model.DTO.ChatDto;
import com.messenger.gateway.model.DTO.CreateChatRequest;
import com.messenger.gateway.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public ResponseEntity<List<ChatDto>> getUserChats(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(chatService.getUserChats(userId));
    }

    @PostMapping
    public ResponseEntity<ChatDto> createChat(@RequestBody CreateChatRequest request) {
        ChatDto chat = chatService.createChat(request);
        return ResponseEntity.ok(chat);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChatDto> getChatById(@PathVariable Long id) {
        ChatDto chat = chatService.getChatById(id);
        return chat != null ? ResponseEntity.ok(chat) : ResponseEntity.notFound().build();
    }
}
