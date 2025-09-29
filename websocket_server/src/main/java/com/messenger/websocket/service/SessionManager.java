package websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SessionManager {

    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, String> userIdToSessionId = new ConcurrentHashMap<>();

    public void addSession(String sessionId, ChannelHandlerContext ctx, String username, Long userId) {
        // Удаляем предыдущую сессию пользователя, если она существует
        String existingSessionId = userIdToSessionId.get(userId);
        if (existingSessionId != null) {
            removeSession(existingSessionId);
        }

        UserSession session = new UserSession(sessionId, ctx, username, userId);
        sessions.put(sessionId, session);
        userIdToSessionId.put(userId, sessionId);

        log.info("User session added: {} (userId: {}, sessionId: {})", username, userId, sessionId);
    }

    public void removeSession(String sessionId) {
        UserSession session = sessions.remove(sessionId);
        if (session != null) {
            userIdToSessionId.remove(session.getUserId());
            log.info("User session removed: {} (userId: {}, sessionId: {})",
                    session.getUsername(), session.getUserId(), sessionId);
        }
    }

    public boolean isAuthenticated(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    public String getUsername(String sessionId) {
        UserSession session = sessions.get(sessionId);
        return session != null ? session.getUsername() : null;
    }

    public Long getUserId(String sessionId) {
        UserSession session = sessions.get(sessionId);
        return session != null ? session.getUserId() : null;
    }

    public ChannelHandlerContext getContext(String sessionId) {
        UserSession session = sessions.get(sessionId);
        return session != null ? session.getContext() : null;
    }

    public ChannelHandlerContext getContextByUserId(Long userId) {
        String sessionId = userIdToSessionId.get(userId);
        return sessionId != null ? getContext(sessionId) : null;
    }

    public boolean isUserOnline(Long userId) {
        return userIdToSessionId.containsKey(userId);
    }

    public int getActiveSessionsCount() {
        return sessions.size();
    }

    /**
     * Получить все каналы участников чата
     */
    public List<io.netty.channel.Channel> getChatChannels(Long chatId) {
        // Здесь нужна логика для получения участников чата из базы данных
        // Пока возвращаем все активные каналы как заглушку
        return sessions.values().stream()
            .map(session -> session.getContext().channel())
            .filter(channel -> channel != null && channel.isActive())
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Получить канал пользователя по ID
     */
    public io.netty.channel.Channel getUserChannel(Long userId) {
        ChannelHandlerContext ctx = getContextByUserId(userId);
        return ctx != null ? ctx.channel() : null;
    }

    /**
     * Отправить сообщение пользователю
     */
    public boolean sendMessageToUser(Long userId, websocket.model.WebSocketMessage message) {
        io.netty.channel.Channel channel = getUserChannel(userId);
        if (channel != null && channel.isActive()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(message);
                channel.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(json));
                return true;
            } catch (Exception e) {
                log.error("Error sending message to user {}: {}", userId, e.getMessage());
            }
        }
        return false;
    }

    // Статический экземпляр для работы с Kafka
    private static SessionManager instance;

    public SessionManager() {
        instance = this;
    }

    /**
     * Статический метод для трансляции сообщений в чат (используется в KafkaMessageListener)
     */
    public static void broadcastMessageToChat(Long chatId, Map<String, Object> messageData) {
        if (instance != null) {
            instance.broadcastToChatInternal(chatId, messageData);
        } else {
            log.warn("SessionManager instance not available for broadcasting");
        }
    }

    /**
     * Внутренний метод для трансляции сообщений в чат
     */
    private void broadcastToChatInternal(Long chatId, Map<String, Object> messageData) {
        try {
            // Создаем WebSocket сообщение
            websocket.model.WebSocketMessage wsMessage = new websocket.model.WebSocketMessage();
            wsMessage.setType(websocket.model.WebSocketMessage.MessageType.CHAT_MESSAGE);
            wsMessage.setContent((String) messageData.get("content"));
            wsMessage.setChatId(chatId);

            if (messageData.containsKey("senderId")) {
                wsMessage.setUserId(((Number) messageData.get("senderId")).longValue());
            }
            if (messageData.containsKey("senderUsername")) {
                wsMessage.setUsername((String) messageData.get("senderUsername"));
            }

            // Получаем все активные каналы (в реальном приложении нужно получать участников чата из БД)
            List<io.netty.channel.Channel> channels = getChatChannels(chatId);

            if (!channels.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(wsMessage);

                for (io.netty.channel.Channel channel : channels) {
                    try {
                        if (channel.isActive()) {
                            channel.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(json));
                            log.debug("Sent message to channel: {}", channel.id());
                        }
                    } catch (Exception e) {
                        log.error("Failed to send message to channel: {}", channel.id(), e);
                    }
                }

                log.info("Broadcasted message to {} channels for chat {}", channels.size(), chatId);
            } else {
                log.debug("No active channels for chat {}", chatId);
            }

        } catch (Exception e) {
            log.error("Error broadcasting message to chat {}: {}", chatId, e.getMessage());
        }
    }

    // Внутренний класс для хранения информации о сессии
    private static class UserSession {
        private final String sessionId;
        private final ChannelHandlerContext context;
        private final String username;
        private final Long userId;

        public UserSession(String sessionId, ChannelHandlerContext context, String username, Long userId) {
            this.sessionId = sessionId;
            this.context = context;
            this.username = username;
            this.userId = userId;
        }

        public String getSessionId() { return sessionId; }
        public ChannelHandlerContext getContext() { return context; }
        public String getUsername() { return username; }
        public Long getUserId() { return userId; }
    }
}

