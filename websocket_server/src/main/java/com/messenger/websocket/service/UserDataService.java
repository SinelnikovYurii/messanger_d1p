package com.messenger.websocket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Сервис для получения данных пользователей из core-api-service
 */
@Slf4j
@Service
public class UserDataService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${core.api.base-url:http://localhost:8082}")
    private String coreApiBaseUrl;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Получить данные пользователя по ID
     */
    public UserData getUserData(Long userId) {
        try {
            log.info("[USER-DATA] Fetching user data for userId={}", userId);

            String url = coreApiBaseUrl + "/api/users/" + userId + "/internal";

            // Создаем заголовки для внутреннего сервиса
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service", "websocket-server");
            headers.set("X-Service-Auth", "internal-service-key");

            HttpEntity<?> entity = new HttpEntity<>(headers);

            // Отправляем запрос
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());

                UserData userData = new UserData();
                userData.setId(jsonNode.get("id").asLong());
                userData.setUsername(jsonNode.get("username").asText());
                userData.setIsOnline(jsonNode.has("isOnline") ? jsonNode.get("isOnline").asBoolean() : false);

                // Парсим lastSeen если оно есть
                if (jsonNode.has("lastSeen") && !jsonNode.get("lastSeen").isNull()) {
                    String lastSeenStr = jsonNode.get("lastSeen").asText();
                    try {
                        userData.setLastSeen(LocalDateTime.parse(lastSeenStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    } catch (Exception e) {
                        log.warn("[USER-DATA] Failed to parse lastSeen: {}", lastSeenStr);
                    }
                }

                log.info("[USER-DATA] Successfully fetched user data: id={}, username={}, isOnline={}, lastSeen={}",
                    userData.getId(), userData.getUsername(), userData.getIsOnline(), userData.getLastSeen());

                return userData;
            }

            log.warn("[USER-DATA] Failed to fetch user data: status={}", response.getStatusCode());
            return null;

        } catch (Exception e) {
            log.error("[USER-DATA] Error fetching user data for userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    @Setter
    @Getter
    public class UserData {
        private Long id;
        private String username;
        private Boolean isOnline;
        private LocalDateTime lastSeen;

    }
}
