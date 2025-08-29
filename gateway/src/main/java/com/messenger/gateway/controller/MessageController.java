package com.messenger.gateway.controller;

import com.messenger.gateway.model.DTO.MessageDto;
import com.messenger.gateway.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chats/{chatId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public ResponseEntity<List<MessageDto>> getMessages(@PathVariable Long chatId) {
        return ResponseEntity.ok(messageService.getChatMessages(chatId));
    }

    @PostMapping
    public ResponseEntity<MessageDto> sendMessage(
            @PathVariable Long chatId,
            @RequestBody MessageDto messageDto,
            Authentication authentication) {

        Long senderId = Long.valueOf(authentication.getName());
        MessageDto message = messageService.sendMessage(chatId, senderId, messageDto.getContent());
        return ResponseEntity.ok(message);
    }
}
