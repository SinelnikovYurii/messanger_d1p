package com.messenger.core.controller;

import com.messenger.core.service.user.FriendshipService;
import com.messenger.core.service.user.UserContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
@Slf4j
public class FriendshipController {

    private final FriendshipService friendshipService;
    private final UserContextResolver userContextResolver;

    @PostMapping("/request")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> sendFriendRequest(
            @RequestBody Map<String, Long> body,
            HttpServletRequest request) {

        Long currentUserId = userContextResolver.resolveUserId(request);
        Long targetUserId = body.get("userId");

        if (targetUserId == null) {
            throw new IllegalArgumentException("Не указан ID пользователя для запроса дружбы");
        }

        log.info("Запрос дружбы: currentUserId={} -> targetUserId={}", currentUserId, targetUserId);
        friendshipService.sendFriendRequest(currentUserId, targetUserId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Запрос дружбы отправлен"));
    }

    @PostMapping("/respond")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> respondToFriendRequest(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long currentUserId = userContextResolver.resolveUserId(request);

        if (body.get("requestId") == null) {
            throw new IllegalArgumentException("Не указан ID запроса дружбы");
        }
        if (body.get("accept") == null) {
            throw new IllegalArgumentException("Поле 'accept' обязательно");
        }

        Long requestId = ((Number) body.get("requestId")).longValue();
        Boolean accept = (Boolean) body.get("accept");

        log.info("Ответ на запрос дружбы: requestId={}, accept={}, currentUserId={}", requestId, accept, currentUserId);

        if (accept) {
            friendshipService.acceptFriendRequest(requestId, currentUserId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Запрос дружбы принят"));
        } else {
            friendshipService.rejectFriendRequest(requestId, currentUserId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Запрос дружбы отклонен"));
        }
    }

    @GetMapping("/incoming")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> getIncomingFriendRequests(HttpServletRequest request) {
        Long currentUserId = userContextResolver.resolveUserId(request);
        log.info("Входящие запросы дружбы для userId={}", currentUserId);
        return ResponseEntity.ok(friendshipService.getIncomingFriendRequests(currentUserId));
    }

    @GetMapping("/outgoing")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> getOutgoingFriendRequests(HttpServletRequest request) {
        Long currentUserId = userContextResolver.resolveUserId(request);
        log.info("Исходящие запросы дружбы для userId={}", currentUserId);
        return ResponseEntity.ok(friendshipService.getOutgoingFriendRequests(currentUserId));
    }
}
