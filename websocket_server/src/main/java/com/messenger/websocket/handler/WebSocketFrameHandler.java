package websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.extern.slf4j.Slf4j;
import websocket.model.MessageType;
import websocket.model.UserSession;
import websocket.model.WebSocketMessage;
import websocket.service.JwtAuthService;
import websocket.service.SessionManager;

import java.util.List;
import java.util.Map;

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
            WebSocketServerProtocolHandler.HandshakeComplete handshake = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            log.info("WebSocket handshake completed for channel: {}. URI: {}", ctx.channel().id(), handshake.requestUri());
            authenticate(ctx, handshake.requestUri());
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    private void authenticate(ChannelHandlerContext ctx, String uri) {
        log.info("Authenticating request for URI: {}", uri); // Добавлено логирование
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        Map<String, List<String>> params = decoder.parameters();
        log.info("Decoded parameters: {}", params); // Добавлено логирование
        List<String> tokenList = params.get("token");

        if (tokenList == null || tokenList.isEmpty()) {
            log.warn("Authentication failed: token is missing. Channel: {}", ctx.channel().id());
            sendErrorMessage(ctx, "Authentication failed: token is missing");
            ctx.close();
            return;
        }

        String token = tokenList.get(0);
        try {
            Long userId = jwtAuthService.validateTokenAndGetUserId(token);
            if (userId == null) {
                log.warn("Authentication failed: invalid token. Channel: {}", ctx.channel().id());
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

            log.info("User {} authenticated and joined WebSocket. Channel: {}", userId, ctx.channel().id());

        } catch (Exception e) {
            log.error("Error during authentication", e);
            sendErrorMessage(ctx, "Authentication error: " + e.getMessage());
            ctx.close();
        }
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String request = frame.text();
        log.debug("Received message: {}", request);

        if (authenticatedUserId == null) {
            sendErrorMessage(ctx, "Not authenticated");
            return;
        }

        try {
            WebSocketMessage message = objectMapper.readValue(request, WebSocketMessage.class);

            switch (message.getType()) {
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

    @SuppressWarnings("unchecked")
    private void handleSendMessage(ChannelHandlerContext ctx, WebSocketMessage message) {
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
        if (this.authenticatedUserId != null) {
            SessionManager.removeUserSession(ctx.channel());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("WebSocket error on channel {}", ctx.channel().id(), cause);
        if (this.authenticatedUserId != null) {
            SessionManager.removeUserSession(ctx.channel());
        }
        ctx.close();
    }
}