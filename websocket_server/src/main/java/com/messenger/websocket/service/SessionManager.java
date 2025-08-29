package websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;

import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import websocket.model.MessageType;
import websocket.model.UserSession;
import websocket.model.WebSocketMessage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

@Slf4j
public class SessionManager {


    private static final Map<Long, UserSession> userSessions = new ConcurrentHashMap<>();


    private static final Map<String, Long> channelToUser = new ConcurrentHashMap<>();


    private static final Map<Long, Set<Long>> chatParticipants = new ConcurrentHashMap<>();

    public static void addUserSession(Long userId, UserSession session) {
        userSessions.put(userId, session);
        channelToUser.put(session.getChannel().id().asLongText(), userId);
        log.info("User {} connected. Total active sessions: {}", userId, userSessions.size());
    }

    public static void broadcastMessageToChat(Long chatId, Map<String, Object> messageData) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            WebSocketMessage wsMessage = new WebSocketMessage(MessageType.CHAT_MESSAGE, messageData);
            String jsonMessage = objectMapper.writeValueAsString(wsMessage);

            List<Channel> channels = getChatChannels(chatId);
            for (Channel channel : channels) {
                if (channel.isActive()) {
                    channel.writeAndFlush(new TextWebSocketFrame(jsonMessage));
                }
            }
        } catch (Exception e) {
            log.error("Error broadcasting message to chat {}", chatId, e);
        }
    }

    public static void removeUserSession(Channel channel) {
        String channelId = channel.id().asLongText();
        Long userId = channelToUser.remove(channelId);
        if (userId != null) {
            UserSession session = userSessions.remove(userId);
            if (session != null && session.getCurrentChatId() != null) {
                Set<Long> participants = chatParticipants.get(session.getCurrentChatId());
                if (participants != null) {
                    participants.remove(userId);
                }
            }
            log.info("User {} disconnected. Total active sessions: {}", userId, userSessions.size());
        }
    }

    public static UserSession getUserSession(Long userId) {
        return userSessions.get(userId);
    }

    public static Set<Long> getAllUserIds() {
        return userSessions.keySet();
    }

    public static int getActiveSessionCount() {
        return userSessions.size();
    }

    public static void addUserToChat(Long userId, Long chatId) {
        UserSession session = userSessions.get(userId);
        if (session != null) {
            session.setCurrentChatId(chatId);

            chatParticipants.computeIfAbsent(chatId, k -> ConcurrentHashMap.newKeySet())
                    .add(userId);
        }
    }

    public static List<Channel> getChatChannels(Long chatId) {
        List<Channel> channels = new ArrayList<>();
        Set<Long> participants = chatParticipants.get(chatId);
        if (participants != null) {
            for (Long userId : participants) {
                UserSession session = userSessions.get(userId);
                if (session != null) {
                    channels.add(session.getChannel());
                }
            }
        }
        return channels;
    }

    public static Channel getUserChannel(Long userId) {
        UserSession session = userSessions.get(userId);
        return session != null ? session.getChannel() : null;
    }
}