package websocket.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Сервис для обновления онлайн-статуса пользователей в базе данных
 */
@Slf4j
@Service
public class OnlineStatusService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${core.api.base-url:http://localhost:8082}")
    private String coreApiBaseUrl;

    /**
     * Обновить онлайн-статус пользователя
     */
    public void updateOnlineStatus(Long userId, boolean isOnline) {
        try {
            log.info("[ONLINE-STATUS] ========================================");
            log.info("[ONLINE-STATUS] Updating online status for user {}: {}", userId, isOnline);
            log.info("[ONLINE-STATUS] Core API URL: {}", coreApiBaseUrl);

            String url = coreApiBaseUrl + "/api/users/" + userId + "/status/online?isOnline=" + isOnline;
            log.info("[ONLINE-STATUS] Full request URL: {}", url);

            // Создаем заголовки для внутреннего сервиса
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service", "websocket-server");
            headers.set("X-Service-Auth", "internal-service-key");

            log.info("[ONLINE-STATUS] Request headers: {}", headers);

            HttpEntity<?> entity = new HttpEntity<>(headers);

            // Отправляем запрос на обновление статуса
            log.info("[ONLINE-STATUS] Sending POST request...");
            ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);

            log.info("[ONLINE-STATUS] Response status: {}", response.getStatusCode());
            log.info("[ONLINE-STATUS] Successfully updated online status for user {} to {}", userId, isOnline);
            log.info("[ONLINE-STATUS] ========================================");

        } catch (Exception e) {
            log.error("[ONLINE-STATUS] ========================================");
            log.error("[ONLINE-STATUS] FAILED to update online status for user {}: {}", userId, e.getMessage());
            log.error("[ONLINE-STATUS] Exception type: {}", e.getClass().getName());
            log.error("[ONLINE-STATUS] Stack trace:", e);
            log.error("[ONLINE-STATUS] ========================================");
            // Не прерываем выполнение, так как это некритично для работы WebSocket
        }
    }

    /**
     * Установить пользователя онлайн
     */
    public void setUserOnline(Long userId) {
        log.info("[ONLINE-STATUS] Setting user {} ONLINE", userId);
        updateOnlineStatus(userId, true);
    }

    /**
     * Установить пользователя оффлайн
     */
    public void setUserOffline(Long userId) {
        log.info("[ONLINE-STATUS] Setting user {} OFFLINE", userId);
        updateOnlineStatus(userId, false);
    }
}
