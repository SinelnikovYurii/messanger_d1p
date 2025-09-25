package com.messenger.gateway.controller;

import com.messenger.gateway.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public Flux<Object> getUserChats(ServerWebExchange exchange) {
        String token = extractToken(exchange);
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(authentication -> Long.valueOf(authentication.getName()))
                .flatMapMany(userId -> chatService.getUserChats(userId, token));
    }

    @PostMapping
    public Mono<Object> createChat(@RequestBody Object request, ServerWebExchange exchange) {
        String token = extractToken(exchange);
        return chatService.createChat(request, token);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Object>> getChatById(@PathVariable Long id, ServerWebExchange exchange) {
        String token = extractToken(exchange);
        return chatService.getChatById(id, token)
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
