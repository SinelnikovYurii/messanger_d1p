package websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import websocket.model.WebSocketMessage;
import websocket.service.JwtAuthService;
import websocket.service.SessionManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final JwtAuthService jwtAuthService;
    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager = new SessionManager();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof TextWebSocketFrame) {
            String request = ((TextWebSocketFrame) frame).text();
            log.debug("Received WebSocket message: {}", request);

            try {
                WebSocketMessage message = objectMapper.readValue(request, WebSocketMessage.class);
                handleMessage(ctx, message);
            } catch (Exception e) {
                log.error("Error processing WebSocket message: {}", e.getMessage());
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
        if (evt == WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
            log.info("WebSocket handshake completed for channel: {}", ctx.channel().id());
            String token = ctx.channel().attr(HttpRequestHandler.TOKEN_ATTRIBUTE).get();
            if (token != null) {
                authenticateWithToken(ctx, token);
            } else {
                log.warn("No token found after handshake for WebSocket connection: {}", ctx.channel().id());
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
        if (!jwtAuthService.validateToken(token)) {
            sendErrorMessage(ctx, "Invalid authentication token");
            ctx.close();
            return;
        }

        String username = jwtAuthService.getUsernameFromToken(token);
        Long userId = jwtAuthService.getUserIdFromToken(token);

        if (username == null || userId == null) {
            sendErrorMessage(ctx, "Invalid token data");
            ctx.close();
            return;
        }

        sessionManager.addSession(ctx.channel().id().asShortText(), ctx, username, userId);

        WebSocketMessage response = new WebSocketMessage();
        response.setType(WebSocketMessage.MessageType.AUTH_SUCCESS);
        response.setContent("Authentication successful");
        response.setUserId(userId);
        response.setUsername(username);
        sendMessage(ctx, response);

        // Дополнительное системное сообщение, чтобы фронт мог явно отреагировать
        WebSocketMessage connected = new WebSocketMessage();
        connected.setType(WebSocketMessage.MessageType.SYSTEM_MESSAGE);
        connected.setContent("CONNECTED");
        connected.setUserId(userId);
        connected.setUsername(username);
        sendMessage(ctx, connected);

        log.info("User {} (ID: {}) authenticated successfully via URL token", username, userId);
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
            sendErrorMessage(ctx, "Not authenticated");
            return;
        }

        log.info("Chat message from user {}: {}",
                sessionManager.getUsername(sessionId), message.getContent());
    }

    private void handlePing(ChannelHandlerContext ctx) {
        WebSocketMessage pong = new WebSocketMessage();
        pong.setType(WebSocketMessage.MessageType.PONG);
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
        errorMessage.setType(WebSocketMessage.MessageType.ERROR);
        errorMessage.setContent(error);
        sendMessage(ctx, errorMessage);
    }
}