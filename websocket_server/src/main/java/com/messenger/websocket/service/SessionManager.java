package websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.Getter;
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

    @Autowired
    private OnlineStatusService onlineStatusService;

    @Autowired
    private UserDataService userDataService;

    public void addSession(String sessionId, ChannelHandlerContext ctx, String username, Long userId) {
        if (sessionId == null || userId == null) {
            log.warn("[SESSION] addSession called with null sessionId or userId: sessionId={}, userId={}", sessionId, userId);
            return;
        }
        synchronized (userId.toString().intern()) {
            // Если sessionId уже существует, удаляем старый userId из userIdToSessionId
            UserSession oldSession = sessions.get(sessionId);
            if (oldSession != null && oldSession.getUserId() != null && !oldSession.getUserId().equals(userId)) {
                userIdToSessionId.remove(oldSession.getUserId());
            }
            // Удаляем предыдущую сессию пользователя, если она существует
            String existingSessionId = userIdToSessionId.get(userId);
            if (existingSessionId != null) {
                log.info("[SESSION] Removing existing session for user {} (ID: {}): {}", username, userId, existingSessionId);
                removeSession(existingSessionId);
            }
            UserSession session = new UserSession(sessionId, ctx, username, userId);
            sessions.put(sessionId, session);
            userIdToSessionId.put(userId, sessionId);

            // Обновляем онлайн-статус в базе данных
            onlineStatusService.setUserOnline(userId);

            // Отправляем уведомление всем о том, что пользователь онлайн
            broadcastUserOnlineStatus(userId, username, true);

            log.info("[SESSION] User session added: {} (userId: {}, sessionId: {})", username, userId, sessionId);
            log.info("[SESSION] Total active sessions: {}", sessions.size());
        }
    }

    public void removeSession(String sessionId) {
        UserSession session = sessions.get(sessionId);
        if (session != null) {
            synchronized (session.getUserId().toString().intern()) {
                sessions.remove(sessionId);
                userIdToSessionId.remove(session.getUserId());

                // Обновляем онлайн-статус в базе данных
                onlineStatusService.setUserOffline(session.getUserId());

                // Отправляем уведомление всем о том, что пользователь оффлайн
                broadcastUserOnlineStatus(session.getUserId(), session.getUsername(), false);

                log.info("[SESSION] User session removed: {} (userId: {}, sessionId: {})",
                        session.getUsername(), session.getUserId(), sessionId);
                log.info("[SESSION] Total active sessions: {}", sessions.size());
            }
        } else {
            log.warn("[SESSION] Attempted to remove non-existent session: {}", sessionId);
        }
    }

    /**
     * Отправить уведомление об изменении онлайн-статуса пользователя всем подключенным
     */
    private void broadcastUserOnlineStatus(Long userId, String username, boolean isOnline) {
        try {
            websocket.model.WebSocketMessage statusMessage = new websocket.model.WebSocketMessage();
            statusMessage.setType(isOnline ? MessageType.USER_ONLINE : MessageType.USER_OFFLINE);
            statusMessage.setUserId(userId);
            statusMessage.setUsername(username);
            statusMessage.setTimestamp(LocalDateTime.now());
            statusMessage.setIsOnline(isOnline);

            // ИСПРАВЛЕНИЕ: Получаем актуальные данные пользователя из базы данных
            if (!isOnline) {
                // Пользователь вышел из сети - получаем актуальное время lastSeen
                try {
                    UserDataService.UserData userData = userDataService.getUserData(userId);
                    if (userData != null && userData.getLastSeen() != null) {
                        statusMessage.setLastSeen(userData.getLastSeen());
                        log.info("[ONLINE-STATUS] Retrieved lastSeen from database: {}", userData.getLastSeen());
                    } else {
                        // Если не удалось получить из базы, используем текущее время
                        statusMessage.setLastSeen(LocalDateTime.now());
                        log.warn("[ONLINE-STATUS] Could not retrieve lastSeen from database, using current time");
                    }
                } catch (Exception e) {
                    log.error("[ONLINE-STATUS] Error retrieving user data: {}", e.getMessage());
                    statusMessage.setLastSeen(LocalDateTime.now());
                }
            } else {
                // Пользователь онлайн - lastSeen должен быть null
                statusMessage.setLastSeen(null);
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            String json = mapper.writeValueAsString(statusMessage);

            log.info("[ONLINE-STATUS] Broadcasting {} status for user {} (ID: {}), lastSeen={}",
                isOnline ? "ONLINE" : "OFFLINE", username, userId, statusMessage.getLastSeen());

            // Отправляем всем подключенным пользователям
            int sentCount = 0;
            for (UserSession session : sessions.values()) {
                try {
                    if (session.getContext() != null && session.getContext().channel() != null && session.getContext().channel().isActive()) {
                        session.getContext().channel().writeAndFlush(
                            new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(json)
                        );
                        sentCount++;
                    }
                } catch (Exception e) {
                    log.error("[ONLINE-STATUS] Failed to send status to session: {}", session.getSessionId(), e);
                }
            }

            log.info("[ONLINE-STATUS] Sent status update to {} active sessions", sentCount);
        } catch (Exception e) {
            log.error("[ONLINE-STATUS] Error broadcasting user status: {}", e.getMessage(), e);
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
     * Получить сессию по sessionId (для тестов)
     */
    public UserSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Получить сессию по userId (для тестов)
     */
    public UserSession getSessionByUserId(Long userId) {
        String sessionId = userIdToSessionId.get(userId);
        return sessionId != null ? sessions.get(sessionId) : null;
    }

    /**
     * Получить все активные сессии (для тестов)
     */
    public java.util.Collection<UserSession> getAllSessions() {
        return sessions.values();
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
    public List<io.netty.channel.Channel> getAllActiveChannels() {
        log.info("[SESSION] Using fallback - getting all active channels");

        List<io.netty.channel.Channel> channels = sessions.values().stream()
            .map(session -> {
                log.debug("[SESSION] Checking session: {} (user: {}, userId: {})",
                    session.getSessionId(), session.getUsername(), session.getUserId());
                return session.getContext().channel();
            })
            .filter(channel -> {
                boolean isActive = channel != null && channel.isActive();
                if (!isActive) {
                    log.debug("[SESSION] Skipping inactive channel: {}",
                        channel != null ? channel.id().asShortText() : "null");
                } else {
                    log.debug("[SESSION] Found active channel: {}", channel.id().asShortText());
                }
                return isActive;
            })
            .collect(Collectors.toList());

        log.info("[SESSION] Fallback: Found {} active channels from {} total sessions",
            channels.size(), sessions.size());

        // Логируем все активные каналы
        for (int i = 0; i < channels.size(); i++) {
            io.netty.channel.Channel ch = channels.get(i);
            String channelId = (ch != null && ch.id() != null) ? ch.id().asShortText() : "null";
            log.info("[SESSION] Fallback channel {}: {}", i + 1, channelId);
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
        if (message == null) {
            return false;
        }
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
            log.info("[BROADCAST] Starting broadcast for chat {} with data: {}", chatId, messageData);

            // Создаем WebSocket сообщение
            websocket.model.WebSocketMessage wsMessage = new websocket.model.WebSocketMessage();

            // ИСПРАВЛЕНО: Определяем тип сообщения на основе поля "type" из Kafka
            String messageTypeStr = (String) messageData.get("type");
            MessageType messageType = MessageType.CHAT_MESSAGE; // По умолчанию

            if (messageTypeStr != null) {
                try {
                    // Пытаемся преобразовать строку в enum
                    if ("MESSAGE_READ".equals(messageTypeStr)) {
                        messageType = MessageType.MESSAGE_READ;
                        log.info("[BROADCAST] Processing MESSAGE_READ notification");
                    } else if ("MESSAGE_UPDATE".equals(messageTypeStr)) {
                        messageType = MessageType.CHAT_MESSAGE; // Можно добавить отдельный тип если нужно
                        log.info("✏[BROADCAST] Processing MESSAGE_UPDATE notification");
                    } else if ("NEW_MESSAGE".equals(messageTypeStr)) {
                        messageType = MessageType.CHAT_MESSAGE;
                        log.info("[BROADCAST] Processing NEW_MESSAGE");
                    }
                } catch (Exception e) {
                    log.warn("⚠[BROADCAST] Could not parse message type: {}, using default", messageTypeStr);
                }
            }

            wsMessage.setType(messageType);
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

            // Для MESSAGE_READ добавляем информацию о читателе
            if (messageType == MessageType.MESSAGE_READ) {
                if (messageData.containsKey("messageId")) {
                    wsMessage.setMessageId(((Number) messageData.get("messageId")).longValue());
                }
                if (messageData.containsKey("readerId")) {
                    Long readerId = ((Number) messageData.get("readerId")).longValue();
                    wsMessage.setReaderId(readerId);
                    wsMessage.setUserId(readerId); // Для совместимости
                }
                if (messageData.containsKey("readerUsername")) {
                    String readerUsername = (String) messageData.get("readerUsername");
                    wsMessage.setReaderUsername(readerUsername);
                    wsMessage.setUsername(readerUsername); // Для совместимости
                }
                // ИСПРАВЛЕНИЕ: Добавляем senderId для MESSAGE_READ событий
                if (messageData.containsKey("senderId")) {
                    Long senderId = ((Number) messageData.get("senderId")).longValue();
                    wsMessage.setSenderId(senderId);
                    log.info("[BROADCAST] MESSAGE_READ: Added senderId={}", senderId);
                }
                log.info("[BROADCAST] MESSAGE_READ: messageId={}, readerId={}, readerUsername={}, senderId={}",
                    messageData.get("messageId"), messageData.get("readerId"), messageData.get("readerUsername"), messageData.get("senderId"));
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

            log.info("[BROADCAST] Created WebSocket message: type={}, messageType={}, chatId={}, senderId={}, content='{}', fileUrl={}",
                    wsMessage.getType(), wsMessage.getMessageType(), wsMessage.getChatId(), wsMessage.getUserId(),
                    wsMessage.getContent(), wsMessage.getFileUrl());

            // Получаем все активные каналы (в реальном приложении нужно получать участников чата из БД)
            List<io.netty.channel.Channel> channels = getChatChannels(chatId);

            if (!channels.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                String json = mapper.writeValueAsString(wsMessage);

                log.info("[BROADCAST] Broadcasting to {} channels for chat {}: {}", channels.size(), chatId, json);

                int successCount = 0;
                int failCount = 0;

                for (io.netty.channel.Channel channel : channels) {
                    try {
                        if (channel.isActive()) {
                            channel.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(json));
                            log.debug("[BROADCAST] Sent message to channel: {}", channel.id().asShortText());
                            successCount++;
                        } else {
                            log.debug("[BROADCAST] Skipped inactive channel: {}", channel.id().asShortText());
                        }
                    } catch (Exception e) {
                        log.error("[BROADCAST] Failed to send message to channel: {}", channel.id().asShortText(), e);
                        failCount++;
                    }
                }

                log.info("[BROADCAST] Broadcast completed for chat {}: {} successful, {} failed",
                        chatId, successCount, failCount);
            } else {
                log.warn("[BROADCAST] No active channels found for chat {}", chatId);
            }

        } catch (Exception e) {
            log.error("[BROADCAST] Error during broadcast for chat {}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Отправить уведомление конкретному пользователю
     * Используется для уведомлений о запросах в друзья и других событий
     */
    public void sendNotificationToUser(Long userId, Map<String, Object> notificationData) {
        try {
            log.info("[NOTIFICATION] Sending notification to user {}: {}", userId, notificationData);

            String sessionId = userIdToSessionId.get(userId);
            if (sessionId == null) {
                log.warn("[NOTIFICATION] User {} is not online, notification will not be delivered", userId);
                return;
            }

            UserSession session = sessions.get(sessionId);
            if (session == null || !session.getContext().channel().isActive()) {
                log.warn("[NOTIFICATION] User {} session is inactive", userId);
                return;
            }

            // Конвертируем данные в JSON и отправляем
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            String json = mapper.writeValueAsString(notificationData);

            session.getContext().channel().writeAndFlush(
                new TextWebSocketFrame(json)
            );

            log.info("[NOTIFICATION] Successfully sent notification to user {}", userId);
        } catch (Exception e) {
            log.error("[NOTIFICATION] Error sending notification to user {}: {}", userId, e.getMessage(), e);
        }
    }

    // Публичный внутренний класс для хранения информации о сессии
    @Getter
    public static class UserSession {
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

    }
}
