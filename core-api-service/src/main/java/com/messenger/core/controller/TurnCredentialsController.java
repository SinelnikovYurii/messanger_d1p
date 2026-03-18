package com.messenger.core.controller;

import com.messenger.core.service.turn.TurnCredentialsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Выдаёт временные TURN credentials по алгоритму HMAC-SHA1 (RFC 8489).
 * Ответственность контроллера: маршрутизация и формирование HTTP-ответа (SRP).
 * Вся криптографическая логика делегирована в {@link TurnCredentialsService} (DIP).
 */
@Slf4j
@RestController
@RequestMapping("/api/turn")
@RequiredArgsConstructor
public class TurnCredentialsController {

    private final TurnCredentialsService turnCredentialsService;

    @GetMapping("/credentials")
    public ResponseEntity<Map<String, Object>> getCredentials(Authentication authentication) {
        try {
            Map<String, Object> credentials = turnCredentialsService.generateCredentials(
                    authentication.getName());
            return ResponseEntity.ok(credentials);
        } catch (Exception e) {
            log.error("[TURN] Failed to generate credentials: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
