package websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.extern.slf4j.Slf4j;
import websocket.model.MessageType;
import websocket.model.UserSession;
import websocket.model.WebSocketMessage;
import websocket.service.JwtAuthService;
import websocket.service.SessionManager;

import java.util.Map;
import java.util.HashMap;

@Slf4j
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final JwtAuthService jwtAuthService;
    private final ObjectMapper objectMapper;
    private Long authenticatedUserId;

    public WebSocketFrameHandler(JwtAuthService jwtAuthService, ObjectMapper objectMapper) {
        this.jwtAuthService = jwtAuthService;
        this.objectMapper = objectMapper;
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            log.info("WebSocket handshake completed for channel: {}", ctx.channel().id());
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String request = frame.text();
        log.debug("Received message: {}", request);

        try {
            WebSocketMessage message = objectMapper.readValue(request, WebSocketMessage.class);

            switch (message.getType()) {
                case JOIN_CHAT:
                    handleJoinChat(ctx, message);
                    break;
                case SEND_MESSAGE:
                    handleSendMessage(ctx, message);
                    break;
                case LEAVE_CHAT:
                    handleLeaveChat(ctx, message);
                    break;
                default:
                    sendErrorMessage(ctx, "Unsupported message type: " + message.getType());
            }
        } catch (Exception e) {
            log.error("Error processing message", e);
            sendErrorMessage(ctx, "Invalid message format");
        }
    }

    private void handleJoinChat(ChannelHandlerContext ctx, WebSocketMessage message) {
        try {

            String token = (String) message.getPayload();

            Long userId = jwtAuthService.validateTokenAndGetUserId(token);
            if (userId == null) {
                sendErrorMessage(ctx, "Invalid authentication token");
                ctx.close();
                return;
            }

            this.authenticatedUserId = userId;


            UserSession session = new UserSession(userId, ctx.channel(), "User" + userId, null);
            SessionManager.addUserSession(userId, session);


            WebSocketMessage response = new WebSocketMessage(MessageType.CHAT_MESSAGE,
                    "Welcome! You are connected as user " + userId);
            ctx.channel().writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(response)));

            log.info("User {} joined WebSocket", userId);

        } catch (Exception e) {
            log.error("Error in join chat", e);
            sendErrorMessage(ctx, "Failed to join chat: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleSendMessage(ChannelHandlerContext ctx, WebSocketMessage message) {
        if (authenticatedUserId == null) {
            sendErrorMessage(ctx, "Not authenticated");
            return;
        }

        try {

            Map<String, Object> payload = (Map<String, Object>) message.getPayload();
            payload.put("senderId", authenticatedUserId);
            payload.put("timestamp", java.time.LocalDateTime.now().toString());


            WebSocketMessage response = new WebSocketMessage(MessageType.CHAT_MESSAGE, payload);
            ctx.channel().writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(response)));

        } catch (Exception e) {
            log.error("Error sending message", e);
            sendErrorMessage(ctx, "Failed to send message");
        }
    }

    private void handleLeaveChat(ChannelHandlerContext ctx, WebSocketMessage message) {
        try {
            SessionManager.removeUserSession(ctx.channel());
            this.authenticatedUserId = null;

            WebSocketMessage response = new WebSocketMessage(MessageType.CHAT_MESSAGE, "You have left the chat");
            ctx.channel().writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(response)));

        } catch (Exception e) {
            log.error("Error leaving chat", e);
            sendErrorMessage(ctx, "Failed to leave chat");
        }
    }

    private void sendErrorMessage(ChannelHandlerContext ctx, String errorMessage) {
        try {
            WebSocketMessage error = new WebSocketMessage(MessageType.ERROR, errorMessage);
            ctx.channel().writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(error)));
        } catch (Exception e) {
            log.error("Failed to send error message", e);
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        log.info("WebSocket handler added for channel: {}", ctx.channel().id());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        log.info("WebSocket handler removed for channel: {}", ctx.channel().id());
        SessionManager.removeUserSession(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("WebSocket error", cause);
        SessionManager.removeUserSession(ctx.channel());
        ctx.close();
    }
}