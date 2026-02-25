package com.messenger.core.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Выдаёт временные TURN credentials по алгоритму HMAC-SHA1 (RFC 8489).
 * coturn поддерживает их нативно через use-auth-secret.
 * TTL = 86400 секунд (24 часа).
 */
@Slf4j
@RestController
@RequestMapping("/api/turn")
public class TurnCredentialsController {

    private static final long TTL_SECONDS = 86400L;

    @Value("${turn.secret:TURN_SECRET_CHANGE_ME_IN_PROD_9911}")
    private String turnSecret;

    @Value("${turn.host:localhost}")
    private String turnHost;

    @Value("${turn.port:3478}")
    private int turnPort;

    @GetMapping("/credentials")
    public ResponseEntity<Map<String, Object>> getCredentials(Authentication authentication) {
        try {
            String username = authentication.getName();
            long timestamp = System.currentTimeMillis() / 1000L + TTL_SECONDS;
            // Формат username для coturn: "timestamp:username"
            String turnUsername = timestamp + ":" + username;
            String credential = generateHmacSha1(turnSecret, turnUsername);

            Map<String, Object> response = Map.of(
                "urls", List.of(
                    "stun:" + turnHost + ":" + turnPort,
                    "turn:" + turnHost + ":" + turnPort,
                    "turn:" + turnHost + ":" + turnPort + "?transport=tcp"
                ),
                "username", turnUsername,
                "credential", credential,
                "ttl", TTL_SECONDS
            );

            log.debug("[TURN] Issued credentials for user: {}", username);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[TURN] Failed to generate credentials: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private String generateHmacSha1(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
        mac.init(secretKey);
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(rawHmac);
    }
}
