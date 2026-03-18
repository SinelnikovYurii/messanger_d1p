package com.messenger.core.service.turn;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Реализация {@link TurnCredentialsService}.
 * Вся криптографическая логика и формирование ответа сосредоточены здесь (SRP).
 * Контроллер зависит только от интерфейса (DIP).
 */
@Slf4j
@Service
public class TurnCredentialsServiceImpl implements TurnCredentialsService {

    private static final long TTL_SECONDS = 86400L;
    private static final String ALGORITHM = "HmacSHA1";

    @Value("${turn.secret:TURN_SECRET_CHANGE_ME_IN_PROD_9911}")
    private String turnSecret;

    @Value("${turn.host:localhost}")
    private String turnHost;

    @Value("${turn.port:3478}")
    private int turnPort;

    @Override
    public Map<String, Object> generateCredentials(String username) {
        long timestamp = System.currentTimeMillis() / 1000L + TTL_SECONDS;
        // Формат username для coturn: "timestamp:username"
        String turnUsername = timestamp + ":" + username;
        String credential = generateHmacSha1(turnSecret, turnUsername);

        log.debug("[TURN] Issued credentials for user: {}", username);

        return Map.of(
                "urls", List.of(
                        "stun:" + turnHost + ":" + turnPort,
                        "turn:" + turnHost + ":" + turnPort,
                        "turn:" + turnHost + ":" + turnPort + "?transport=tcp"
                ),
                "username", turnUsername,
                "credential", credential,
                "ttl", TTL_SECONDS
        );
    }

    /**
     * Вычисляет HMAC-SHA1 подпись.
     *
     * @param secret секретный ключ
     * @param data   данные для подписи
     * @return Base64-закодированная подпись
     * @throws IllegalStateException если алгоритм недоступен (не ожидается в JVM)
     */
    private String generateHmacSha1(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA1", e);
        }
    }
}
