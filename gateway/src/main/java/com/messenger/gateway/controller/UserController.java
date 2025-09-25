package com.messenger.gateway.controller;

import com.messenger.gateway.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public Flux<Object> getAllUsers(ServerWebExchange exchange) {
        String token = extractToken(exchange);
        return userService.getAllUsers(token);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Object>> getUserById(@PathVariable Long id, ServerWebExchange exchange) {
        String token = extractToken(exchange);
        return userService.getUserById(id, token)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public Mono<Object> updateUser(@PathVariable Long id, @RequestBody Object userRequest, ServerWebExchange exchange) {
        String token = extractToken(exchange);
        return userService.updateUser(id, userRequest, token);
    }

    @GetMapping("/search")
    public Flux<Object> searchUsers(@RequestParam String q, ServerWebExchange exchange) {
        String token = extractToken(exchange);
        return userService.searchUsers(q, token);
    }

    private String extractToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return "";
    }
}
