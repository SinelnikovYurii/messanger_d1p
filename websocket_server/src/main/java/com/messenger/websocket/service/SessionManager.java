package websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import websocket.model.MessageType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SessionManager {

    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, String> userIdToSessionId = new ConcurrentHashMap<>();

    @Autowired
    private ChatParticipantService chatParticipantService;

    public void addSession(String sessionId, ChannelHandlerContext ctx, String username, Long userId) {
        // Удаляем предыдущую сессию пользователя, если она существует
        String existingSessionId = userIdToSessionId.get(userId);
        if (existingSessionId != null) {
            log.info("[SESSION] Removing existing session for user {} (ID: {}): {}", username, userId, existingSessionId);
            removeSession(existingSessionId);
        }

        UserSession session = new UserSession(sessionId, ctx, username, userId);
        sessions.put(sessionId, session);
        userIdToSessionId.put(userId, sessionId);

        log.info("[SESSION] User session added: {} (userId: {}, sessionId: {})", username, userId, sessionId);
        log.info("[SESSION] Total active sessions: {}", sessions.size());
    }

    public void removeSession(String sessionId) {
        UserSession session = sessions.remove(sessionId);
        if (session != null) {
            userIdToSessionId.remove(session.getUserId());
            log.info("[SESSION] User session removed: {} (userId: {}, sessionId: {})",
                    session.getUsername(), session.getUserId(), sessionId);
            log.info("[SESSION] Total active sessions: {}", sessions.size());
        } else {
            log.warn("[SESSION] Attempted to remove non-existent session: {}", sessionId);
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
     * ИСПРАВЛЕНО: теперь получает реальных участников чата из базы данных с fallback
     */
    public List<io.netty.channel.Channel> getChatChannels(Long chatId) {
        log.info("[SESSION] Getting channels for chat {} - fetching real participants from database", chatId);

        try {
            // Получаем список участников чата из базы данных
            List<Long> participantIds = chatParticipantService.getChatParticipants(chatId);
            log.info("[SESSION] Found {} participants for chat {}: {}", participantIds.size(), chatId, participantIds);

            // Если нет участников (ошибка API или пустой чат), используем fallback
            if (participantIds.isEmpty()) {
                log.warn("[SESSION] No participants found for chat {}, using fallback (all connected users)", chatId);
                return getAllActiveChannels();
            }

            // Получаем активные каналы только для участников чата
            List<io.netty.channel.Channel> channels = participantIds.stream()
                .map(userId -> {
                    String sessionId = userIdToSessionId.get(userId);
                    if (sessionId != null) {
                        UserSession session = sessions.get(sessionId);
                        if (session != null) {
                            io.netty.channel.Channel channel = session.getContext().channel();
                            if (channel != null && channel.isActive()) {
                                log.debug("[SESSION] Found active channel for user {} (session: {})",
                                    userId, sessionId);
                                return channel;
                            } else {
                                log.debug("[SESSION] User {} has inactive channel (session: {})",
                                    userId, sessionId);
                            }
                        } else {
                            log.debug("[SESSION] User {} has no session data", userId);
                        }
                    } else {
                        log.debug("[SESSION] User {} is not connected", userId);
                    }
                    return null;
                })
                .filter(channel -> channel != null)
                .collect(Collectors.toList());

            log.info("[SESSION] Found {} active channels for {} participants in chat {}",
                channels.size(), participantIds.size(), chatId);

            // Логируем детали каналов
            for (int i = 0; i < channels.size(); i++) {
                log.debug("[SESSION] Active channel {}: {}", i + 1, channels.get(i).id().asShortText());
            }

            return channels;

        } catch (Exception e) {
            log.error("[SESSION] Error getting chat channels for chat {}: {}", chatId, e.getMessage(), e);
            // В случае ошибки используем fallback - все активные каналы
            log.warn("[SESSION] Using fallback - returning all active channels for chat {}", chatId);
            return getAllActiveChannels();
        }
    }

    /**
     * Fallback метод - получить все активные каналы
     */
    private List<io.netty.channel.Channel> getAllActiveChannels() {
        log.info("[SESSION] Using fallback - getting all active channels");

        List<io.netty.channel.Channel> channels = sessions.values().stream()
            .map(session -> {
                log.debug("🔗 [SESSION] Checking session: {} (user: {}, userId: {})",
                    session.getSessionId(), session.getUsername(), session.getUserId());
                return session.getContext().channel();
            })
            .filter(channel -> {
                boolean isActive = channel != null && channel.isActive();
                if (!isActive) {
                    log.debug("⚠️ [SESSION] Skipping inactive channel: {}",
                        channel != null ? channel.id().asShortText() : "null");
                } else {
                    log.debug("✅ [SESSION] Found active channel: {}", channel.id().asShortText());
                }
                return isActive;
            })
            .collect(Collectors.toList());

        log.info("📡 [SESSION] Fallback: Found {} active channels from {} total sessions",
            channels.size(), sessions.size());

        // Логируем все активные каналы
        for (int i = 0; i < channels.size(); i++) {
            log.info("📄 [SESSION] Fallback channel {}: {}", i + 1, channels.get(i).id().asShortText());
        }

        return channels;
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

    /**
     * Метод для трансляции сообщений в чат (теперь используется напрямую)
     */
    public void broadcastMessageToChat(Long chatId, Map<String, Object> messageData) {
        broadcastToChatInternal(chatId, messageData);
    }

    /**
     * Внутренний метод для трансляции сообщений в чат
     */
    private void broadcastToChatInternal(Long chatId, Map<String, Object> messageData) {
        try {
            log.info("🔊 [BROADCAST] Starting broadcast for chat {} with data: {}", chatId, messageData);

            // Создаем WebSocket сообщение
            websocket.model.WebSocketMessage wsMessage = new websocket.model.WebSocketMessage();
            wsMessage.setType(MessageType.CHAT_MESSAGE);
            wsMessage.setContent((String) messageData.get("content"));
            wsMessage.setChatId(chatId);

            // Устанавливаем текущее время вместо парсинга
            wsMessage.setTimestamp(LocalDateTime.now());

            // Устанавливаем ID сообщения если есть
            if (messageData.containsKey("messageId")) {
                wsMessage.setId(((Number) messageData.get("messageId")).longValue());
            }

            // Устанавливаем данные отправителя
            if (messageData.containsKey("senderId")) {
                wsMessage.setUserId(((Number) messageData.get("senderId")).longValue());
                wsMessage.setSenderId(((Number) messageData.get("senderId")).longValue());
            }
            if (messageData.containsKey("senderUsername")) {
                wsMessage.setUsername((String) messageData.get("senderUsername"));
                wsMessage.setSenderUsername((String) messageData.get("senderUsername"));
            }

            // ВАЖНО: Добавляем тип сообщения (TEXT, IMAGE, FILE)
            if (messageData.containsKey("messageType")) {
                wsMessage.setMessageType((String) messageData.get("messageType"));
            }

            // ВАЖНО: Добавляем метаданные файлов если есть
            if (messageData.containsKey("fileUrl")) {
                wsMessage.setFileUrl((String) messageData.get("fileUrl"));
                log.info("📎 [BROADCAST] Message contains file: {}", messageData.get("fileUrl"));
            }
            if (messageData.containsKey("fileName")) {
                wsMessage.setFileName((String) messageData.get("fileName"));
            }
            if (messageData.containsKey("fileSize")) {
                wsMessage.setFileSize(((Number) messageData.get("fileSize")).longValue());
            }
            if (messageData.containsKey("mimeType")) {
                wsMessage.setMimeType((String) messageData.get("mimeType"));
            }
            if (messageData.containsKey("thumbnailUrl")) {
                wsMessage.setThumbnailUrl((String) messageData.get("thumbnailUrl"));
            }

            log.info("📄 [BROADCAST] Created WebSocket message: type={}, messageType={}, chatId={}, senderId={}, content='{}', fileUrl={}",
                    wsMessage.getType(), wsMessage.getMessageType(), wsMessage.getChatId(), wsMessage.getUserId(),
                    wsMessage.getContent(), wsMessage.getFileUrl());

            // Получаем все активные каналы (в реальном приложении нужно получать участников чата из БД)
            List<io.netty.channel.Channel> channels = getChatChannels(chatId);

            if (!channels.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                String json = mapper.writeValueAsString(wsMessage);

                log.info("📡 [BROADCAST] Broadcasting to {} channels for chat {}: {}", channels.size(), chatId, json);

                int successCount = 0;
                int failCount = 0;

                for (io.netty.channel.Channel channel : channels) {
                    try {
                        if (channel.isActive()) {
                            channel.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(json));
                            log.debug("✅ [BROADCAST] Sent message to channel: {}", channel.id().asShortText());
                            successCount++;
                        } else {
                            log.debug("⚠️ [BROADCAST] Skipped inactive channel: {}", channel.id().asShortText());
                        }
                    } catch (Exception e) {
                        log.error("❌ [BROADCAST] Failed to send message to channel: {}", channel.id().asShortText(), e);
                        failCount++;
                    }
                }

                log.info("✅ [BROADCAST] Broadcast completed for chat {}: {} successful, {} failed",
                        chatId, successCount, failCount);
            } else {
                log.warn("⚠️ [BROADCAST] No active channels found for chat {}", chatId);
            }

        } catch (Exception e) {
            log.error("❌ [BROADCAST] Error during broadcast for chat {}: {}", chatId, e.getMessage(), e);
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
