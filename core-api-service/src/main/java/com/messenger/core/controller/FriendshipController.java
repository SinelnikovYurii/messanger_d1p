package com.messenger.core.controller;

import com.messenger.core.service.FriendshipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
@Slf4j
public class FriendshipController {
    private final FriendshipService friendshipService;

    @PostMapping("/request")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> sendFriendRequest(
            @RequestBody Map<String, Long> request,
            HttpServletRequest httpRequest) {
        log.info("Получен запрос на отправку запроса дружбы к пользователю с ID: {}", request.get("userId"));
        String userIdHeader = httpRequest.getHeader("x-user-id");
        log.info("ID текущего пользователя из заголовка: {}", userIdHeader);
        if (userIdHeader == null) {
            log.error("Не указан ID пользователя в заголовке запроса");
            return ResponseEntity.badRequest().build();
        }
        try {
            Long currentUserId = Long.parseLong(userIdHeader);
            Long targetUserId = request.get("userId");
            if (targetUserId == null) {
                return ResponseEntity.badRequest().body("Не указан ID пользователя для запроса дружбы");
            }
            friendshipService.sendFriendRequest(currentUserId, targetUserId);
            return ResponseEntity.ok().body(Map.of("success", true, "message", "Запрос дружбы отправлен"));
        } catch (NumberFormatException e) {
            log.error("Некорректный формат ID пользователя: {}", userIdHeader);
            return ResponseEntity.badRequest().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Ошибка при отправке запроса дружбы: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/respond")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> respondToFriendRequest(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        log.info("Получен ответ на запрос дружбы: {}", request);
        String userIdHeader = httpRequest.getHeader("x-user-id");
        if (userIdHeader == null) {
            log.error("Не указан ID пользователя в заголовке запроса");
            return ResponseEntity.badRequest().build();
        }
        try {
            Long currentUserId = Long.parseLong(userIdHeader);
            if (request.get("requestId") == null) {
                log.error("Не указан ID запроса дружбы в теле запроса");
                return ResponseEntity.badRequest().body(Map.of("error", "Не указан ID запроса дружбы"));
            }
            Long requestId = ((Number) request.get("requestId")).longValue();
            Boolean accept = (Boolean) request.get("accept");
            log.info("Обработка запроса дружбы - requestId: {}, accept: {}, currentUserId: {}", requestId, accept, currentUserId);
            if (accept == null) {
                log.error("Поле 'accept' отсутствует в запросе или равно null");
                return ResponseEntity.badRequest().body(Map.of("error", "Поле 'accept' обязательно"));
            }
            if (accept) {
                friendshipService.acceptFriendRequest(requestId, currentUserId);
                return ResponseEntity.ok().body(Map.of("success", true, "message", "Запрос дружбы принят"));
            } else {
                friendshipService.rejectFriendRequest(requestId, currentUserId);
                return ResponseEntity.ok().body(Map.of("success", true, "message", "Запрос дружбы отклонен"));
            }
        } catch (NumberFormatException e) {
            log.error("Некорректный формат ID: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Ошибка при ответе на запрос дружбы: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/incoming")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> getIncomingFriendRequests(HttpServletRequest httpRequest) {
        log.info("Получен запрос на получение входящих запросов дружбы");
        String userIdHeader = httpRequest.getHeader("x-user-id");
        if (userIdHeader == null) {
            log.error("Не указан ID пользователя в заголовке запроса");
            return ResponseEntity.badRequest().build();
        }
        try {
            Long currentUserId = Long.parseLong(userIdHeader);
            return ResponseEntity.ok(friendshipService.getIncomingFriendRequests(currentUserId));
        } catch (NumberFormatException e) {
            log.error("Некорректный формат ID пользователя: {}", userIdHeader);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/outgoing")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> getOutgoingFriendRequests(HttpServletRequest httpRequest) {
        log.info("Получен запрос на получение исходящих запросов дружбы");
        String userIdHeader = httpRequest.getHeader("x-user-id");
        if (userIdHeader == null) {
            log.error("Не указан ID пользователя в заголовке запроса");
            return ResponseEntity.badRequest().build();
        }
        try {
            Long currentUserId = Long.parseLong(userIdHeader);
            return ResponseEntity.ok(friendshipService.getOutgoingFriendRequests(currentUserId));
        } catch (NumberFormatException e) {
            log.error("Некорректный формат ID пользователя: {}", userIdHeader);
            return ResponseEntity.badRequest().build();
        }
    }
}

