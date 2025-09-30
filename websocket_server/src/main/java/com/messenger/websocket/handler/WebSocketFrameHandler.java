package websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import websocket.model.WebSocketMessage;
import websocket.model.MessageType;
import websocket.service.JwtAuthService;
import websocket.service.SessionManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final JwtAuthService jwtAuthService;
    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof TextWebSocketFrame) {
            String request = ((TextWebSocketFrame) frame).text();
            log.info("🔄 [WEBSOCKET] Received message from channel {}: {}", ctx.channel().id().asShortText(), request);

            try {
                WebSocketMessage message = objectMapper.readValue(request, WebSocketMessage.class);
                log.info("📩 [WEBSOCKET] Parsed message - Type: {}, Content: {}, ChatId: {}",
                    message.getType(), message.getContent(), message.getChatId());
                handleMessage(ctx, message);
            } catch (Exception e) {
                log.error("❌ [WEBSOCKET] Error processing message from {}: {}", ctx.channel().id().asShortText(), e.getMessage(), e);
                sendErrorMessage(ctx, "Invalid message format");
            }
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // Просто логируем установку TCP соединения. Аутентификацию переносим на завершение рукопожатия.
        log.info("WebSocket TCP connection active (before handshake): {}", ctx.channel().id());
        super.channelActive(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        log.info("🔔 [WEBSOCKET] User event triggered: {} for channel: {}", evt, ctx.channel().id());

        if (evt == WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
            log.info("✅ [WEBSOCKET] WebSocket handshake completed for channel: {}", ctx.channel().id());
            String token = ctx.channel().attr(HttpRequestHandler.TOKEN_ATTRIBUTE).get();
            if (token != null) {
                log.info("🔑 [WEBSOCKET] Found token in channel attributes, proceeding with authentication");
                authenticateWithToken(ctx, token);
            } else {
                log.error("❌ [WEBSOCKET] No token found after handshake for WebSocket connection: {}", ctx.channel().id());
                sendErrorMessage(ctx, "Authentication token required");
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("WebSocket connection closed: {}", ctx.channel().id());
        sessionManager.removeSession(ctx.channel().id().asShortText());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("WebSocket error: {}", cause.getMessage(), cause);
        ctx.close();
    }

    private void authenticateWithToken(ChannelHandlerContext ctx, String token) {
        log.info("🔐 [AUTH] Starting authentication for channel: {}", ctx.channel().id().asShortText());
        log.info("🔐 [AUTH] Token received: {}...", token.substring(0, Math.min(token.length(), 20)));

        if (!jwtAuthService.validateToken(token)) {
            log.error("❌ [AUTH] Token validation failed for channel: {}", ctx.channel().id().asShortText());
            sendErrorMessage(ctx, "Invalid authentication token");
            ctx.close();
            return;
        }

        log.info("✅ [AUTH] Token validation successful, extracting user data");
        String username = jwtAuthService.getUsernameFromToken(token);
        Long userId = jwtAuthService.getUserIdFromToken(token);

        if (username == null || userId == null) {
            log.error("❌ [AUTH] Failed to extract user data - username: {}, userId: {}", username, userId);
            sendErrorMessage(ctx, "Invalid token data");
            ctx.close();
            return;
        }

        log.info("📝 [AUTH] Adding session for user: {} (ID: {})", username, userId);
        sessionManager.addSession(ctx.channel().id().asShortText(), ctx, username, userId);

        WebSocketMessage response = new WebSocketMessage();
        response.setType(MessageType.AUTH_SUCCESS);
        response.setContent("Authentication successful");
        response.setUserId(userId);
        response.setUsername(username);
        sendMessage(ctx, response);

        // Дополнительное системное сообщение, чтобы фронт мог явно отреагировать
        WebSocketMessage connected = new WebSocketMessage();
        connected.setType(MessageType.SYSTEM_MESSAGE);
        connected.setContent("CONNECTED");
        connected.setUserId(userId);
        connected.setUsername(username);
        sendMessage(ctx, connected);

        log.info("🎉 [AUTH] User {} (ID: {}) authenticated successfully via URL token", username, userId);
    }

    private void handleMessage(ChannelHandlerContext ctx, WebSocketMessage message) {
        switch (message.getType()) {
            case AUTH:
                handleAuth(ctx, message);
                break;
            case CHAT_MESSAGE:
                handleChatMessage(ctx, message);
                break;
            case PING:
                handlePing(ctx);
                break;
            default:
                log.warn("Unknown message type: {}", message.getType());
                sendErrorMessage(ctx, "Unknown message type");
        }
    }

    private void handleAuth(ChannelHandlerContext ctx, WebSocketMessage message) {
        String sessionId = ctx.channel().id().asShortText();
        if (sessionManager.isAuthenticated(sessionId)) {
            sendErrorMessage(ctx, "Already authenticated");
            return;
        }

        String token = message.getToken();
        authenticateWithToken(ctx, token);
    }

    private void handleChatMessage(ChannelHandlerContext ctx, WebSocketMessage message) {
        String sessionId = ctx.channel().id().asShortText();
        if (!sessionManager.isAuthenticated(sessionId)) {
            log.warn("🚫 [WEBSOCKET] Unauthenticated user tried to send message from channel: {}", sessionId);
            sendErrorMessage(ctx, "Not authenticated");
            return;
        }

        String username = sessionManager.getUsername(sessionId);
        Long userId = sessionManager.getUserId(sessionId);

        log.info("💬 [WEBSOCKET] Processing chat message - User: {} (ID: {}), ChatId: {}, Content: '{}'",
            username, userId, message.getChatId(), message.getContent());

        try {
            // Создаем объект сообщения для отправки в Kafka
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("content", message.getContent());
            messageData.put("senderId", userId);
            messageData.put("senderUsername", username);
            messageData.put("chatId", message.getChatId());
            messageData.put("messageType", "TEXT");
            messageData.put("timestamp", LocalDateTime.now().toString());

            String jsonMessage = objectMapper.writeValueAsString(messageData);
            String chatKey = String.valueOf(message.getChatId());

            log.info("📤 [KAFKA] Sending message to Kafka - Topic: 'chat-messages', Key: {}, Message: {}",
                chatKey, jsonMessage);

            // Отправляем сообщение в Kafka
            kafkaTemplate.send("chat-messages", chatKey, jsonMessage)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("✅ [KAFKA] Message sent successfully - Offset: {}, Partition: {}",
                            result.getRecordMetadata().offset(), result.getRecordMetadata().partition());
                    } else {
                        log.error("❌ [KAFKA] Failed to send message to Kafka: {}", ex.getMessage(), ex);
                    }
                });

            // Отправляем подтверждение отправителю
            WebSocketMessage confirmation = new WebSocketMessage();
            confirmation.setType(MessageType.MESSAGE_SENT);
            confirmation.setContent("Message sent successfully");
            sendMessage(ctx, confirmation);

            log.info("✅ [WEBSOCKET] Message processing completed for user {} in chat {}", username, message.getChatId());

        } catch (Exception e) {
            log.error("❌ [WEBSOCKET] Error processing chat message from user {} in chat {}: {}",
                username, message.getChatId(), e.getMessage(), e);
            sendErrorMessage(ctx, "Failed to process message");
        }
    }

    private void handlePing(ChannelHandlerContext ctx) {
        WebSocketMessage pong = new WebSocketMessage();
        pong.setType(MessageType.PONG);
        sendMessage(ctx, pong);
    }

    private void sendMessage(ChannelHandlerContext ctx, WebSocketMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            ctx.writeAndFlush(new TextWebSocketFrame(json));
        } catch (Exception e) {
            log.error("Error sending WebSocket message: {}", e.getMessage());
        }
    }

    private void sendErrorMessage(ChannelHandlerContext ctx, String error) {
        WebSocketMessage errorMessage = new WebSocketMessage();
        errorMessage.setType(MessageType.ERROR);
        errorMessage.setContent(error);
        sendMessage(ctx, errorMessage);
    }
}