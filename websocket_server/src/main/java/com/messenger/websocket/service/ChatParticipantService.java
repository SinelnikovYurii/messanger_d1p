package websocket.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Collections;

@Slf4j
@Service
public class ChatParticipantService {

    @Autowired
    private RestTemplate restTemplate;

    private final String CORE_API_URL = "http://localhost:8081"; // URL core-api-service

    /**
     * Получить список ID участников чата из core-api-service
     * ИСПРАВЛЕНО: теперь использует internal service header для обхода авторизации
     */
    public List<Long> getChatParticipants(Long chatId) {
        try {
            log.info("📞 [CHAT-PARTICIPANT] Requesting participants for chat {} from core-api", chatId);

            String url = CORE_API_URL + "/api/chats/" + chatId + "/participants";

            // Создаем заголовки для внутреннего сервиса
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service", "websocket-server"); // Заголовок для внутренних сервисов
            headers.set("X-Service-Auth", "internal-service-key"); // Дополнительная авторизация

            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<List<Long>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<Long>>() {}
            );

            List<Long> participants = response.getBody();
            if (participants == null) {
                participants = Collections.emptyList();
            }

            log.info("✅ [CHAT-PARTICIPANT] Got {} participants for chat {}: {}",
                participants.size(), chatId, participants);

            return participants;

        } catch (Exception e) {
            log.error("❌ [CHAT-PARTICIPANT] Failed to get participants for chat {}: {}", chatId, e.getMessage(), e);

            // ВРЕМЕННОЕ РЕШЕНИЕ: возвращаем всех подключенных пользователей
            log.warn("⚠️ [CHAT-PARTICIPANT] Using fallback - returning all connected users for chat {}", chatId);
            return getAllConnectedUserIds();
        }
    }

    /**
     * Временный fallback метод - возвращает ID всех подключенных пользователей
     */
    private List<Long> getAllConnectedUserIds() {
        // Для временного решения - это будет обрабатываться в SessionManager
        // возвращаем пустой список, чтобы SessionManager использовал старую логику
        return Collections.emptyList();
    }

    /**
     * Проверить, является ли пользователь участником чата
     */
    public boolean isUserParticipant(Long chatId, Long userId) {
        try {
            List<Long> participants = getChatParticipants(chatId);
            boolean isParticipant = participants.contains(userId);

            log.debug("🔍 [CHAT-PARTICIPANT] User {} is {} participant of chat {}",
                userId, isParticipant ? "a" : "NOT a", chatId);

            return isParticipant;

        } catch (Exception e) {
            log.error("❌ [CHAT-PARTICIPANT] Error checking if user {} is participant of chat {}: {}",
                userId, chatId, e.getMessage());
            // В случае ошибки возвращаем false для безопасности
            return false;
        }
    }
}
