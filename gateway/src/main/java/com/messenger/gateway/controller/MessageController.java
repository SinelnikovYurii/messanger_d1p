package com.messenger.gateway.controller;

import com.messenger.gateway.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/chats/{chatId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public Flux<Object> getMessages(
            @PathVariable Long chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            ServerWebExchange exchange) {
        String token = extractToken(exchange);
        return messageService.getChatMessages(chatId, token, page, size);
    }

    @PostMapping
    public Mono<Object> sendMessage(
            @PathVariable Long chatId,
            @RequestBody Map<String, String> messageRequest,
            ServerWebExchange exchange) {

        String token = extractToken(exchange);
        String content = messageRequest.get("content");

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(authentication -> Long.valueOf(authentication.getName()))
                .flatMap(senderId -> messageService.sendMessage(chatId, senderId, content, token));
    }

    @GetMapping("/{messageId}")
    public Mono<ResponseEntity<Object>> getMessageById(
            @PathVariable Long chatId,
            @PathVariable Long messageId,
            ServerWebExchange exchange) {

        String token = extractToken(exchange);
        return messageService.getMessageById(messageId, token)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private String extractToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return "";
    }
}
