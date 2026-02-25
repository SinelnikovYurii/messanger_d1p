package com.messenger.websocket.handler.message;

import com.messenger.websocket.model.MessageType;
import com.messenger.websocket.model.WebSocketMessage;
import com.messenger.websocket.service.JwtAuthService;
import com.messenger.websocket.service.SessionManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AuthMessageHandler implements MessageHandler {

    private final JwtAuthService jwtAuthService;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Override
    public void handle(ChannelHandlerContext ctx, WebSocketMessage message) {
        String sessionId = ctx.channel().id().asShortText();
        if (sessionManager.isAuthenticated(sessionId)) {
            sendError(ctx, "Already authenticated");
            return;
        }
        String token = message.getToken();
        if (token == null || token.isBlank()) {
            sendError(ctx, "Token is required");
            return;
        }
        if (!jwtAuthService.validateToken(token)) {
            sendError(ctx, "Invalid authentication token");
            ctx.close();
            return;
        }
        String username = jwtAuthService.getUsernameFromToken(token);
        Long userId = jwtAuthService.getUserIdFromToken(token);
        if (username == null || userId == null) {
            sendError(ctx, "Invalid token data");
            ctx.close();
            return;
        }
        sessionManager.addSession(sessionId, ctx, username, userId);

        WebSocketMessage response = new WebSocketMessage();
        response.setType(MessageType.AUTH_SUCCESS);
        response.setContent("Authentication successful");
        response.setUserId(userId);
        response.setUsername(username);
        send(ctx, response);
        log.info("[AUTH] User {} (ID: {}) authenticated via message", username, userId);
    }

    private void send(ChannelHandlerContext ctx, WebSocketMessage msg) {
        try {
            ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(msg)));
        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage());
        }
    }

    private void sendError(ChannelHandlerContext ctx, String error) {
        WebSocketMessage err = new WebSocketMessage();
        err.setType(MessageType.ERROR);
        err.setContent(error);
        send(ctx, err);
    }
}
