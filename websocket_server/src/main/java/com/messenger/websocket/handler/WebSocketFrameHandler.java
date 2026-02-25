package com.messenger.websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.websocket.handler.message.*;
import com.messenger.websocket.model.MessageType;
import com.messenger.websocket.model.WebSocketMessage;
import com.messenger.websocket.service.CallSessionManager;
import com.messenger.websocket.service.JwtAuthService;
import com.messenger.websocket.service.SessionManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.EnumMap;
import java.util.Map;

@Slf4j
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final JwtAuthService jwtAuthService;
    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final CallSessionManager callSessionManager;

    /** Registry: тип сообщения → обработчик */
    private final Map<MessageType, MessageHandler> handlers = new EnumMap<>(MessageType.class);

    public WebSocketFrameHandler(JwtAuthService jwtAuthService,
                                  ObjectMapper objectMapper,
                                  SessionManager sessionManager,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  CallSessionManager callSessionManager) {
        this.jwtAuthService     = jwtAuthService;
        this.objectMapper       = objectMapper;
        this.sessionManager     = sessionManager;
        this.kafkaTemplate      = kafkaTemplate;
        this.callSessionManager = callSessionManager;
        registerHandlers();
    }

    private void registerHandlers() {
        AuthMessageHandler     auth      = new AuthMessageHandler(jwtAuthService, sessionManager, objectMapper);
        ChatMessageHandler     chat      = new ChatMessageHandler(sessionManager, kafkaTemplate, objectMapper);
        PingMessageHandler     ping      = new PingMessageHandler(objectMapper);
        SignalingMessageHandler signaling = new SignalingMessageHandler(sessionManager, callSessionManager, objectMapper);

        handlers.put(MessageType.AUTH,          auth);
        handlers.put(MessageType.CHAT_MESSAGE,  chat);
        handlers.put(MessageType.PING,          ping);

        // WebRTC сигналинг
        handlers.put(MessageType.CALL_OFFER,    signaling);
        handlers.put(MessageType.CALL_ANSWER,   signaling);
        handlers.put(MessageType.ICE_CANDIDATE, signaling);
        handlers.put(MessageType.CALL_REJECT,   signaling);
        handlers.put(MessageType.CALL_END,      signaling);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Netty lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (!(frame instanceof TextWebSocketFrame)) return;

        String request = ((TextWebSocketFrame) frame).text();
        log.info("[WS] Received from {}: {}", ctx.channel().id().asShortText(), request);

        try {
            WebSocketMessage message = objectMapper.readValue(request, WebSocketMessage.class);
            dispatch(ctx, message);
        } catch (Exception e) {
            log.error("[WS] Parse error from {}: {}", ctx.channel().id().asShortText(), e.getMessage());
            sendError(ctx, "Invalid message format");
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("[WS] TCP connection active (before handshake): {}", ctx.channel().id());
        super.channelActive(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
            log.info("[WS] Handshake complete for channel: {}", ctx.channel().id());
            String token = ctx.channel().attr(HttpRequestHandler.TOKEN_ATTRIBUTE).get();
            if (token != null) {
                authenticateWithToken(ctx, token);
            } else {
                log.error("[WS] No token found after handshake for: {}", ctx.channel().id());
                sendError(ctx, "Authentication token required");
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("[WS] Connection closed: {}", ctx.channel().id());
        sessionManager.removeSession(ctx.channel().id().asShortText());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("[WS] Error: {}", cause.getMessage(), cause);
        ctx.close();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internal
    // ──────────────────────────────────────────────────────────────────────────

    private void dispatch(ChannelHandlerContext ctx, WebSocketMessage message) {
        if (message.getType() == null) {
            sendError(ctx, "Message type is required");
            return;
        }
        MessageHandler handler = handlers.get(message.getType());
        if (handler != null) {
            handler.handle(ctx, message);
        } else {
            log.warn("[WS] No handler for type: {}", message.getType());
            sendError(ctx, "Unknown message type: " + message.getType());
        }
    }

    private void authenticateWithToken(ChannelHandlerContext ctx, String token) {
        log.info("[AUTH] Authenticating channel: {}", ctx.channel().id().asShortText());

        if (!jwtAuthService.validateToken(token)) {
            sendError(ctx, "Invalid authentication token");
            ctx.close();
            return;
        }

        String username = jwtAuthService.getUsernameFromToken(token);
        Long userId     = jwtAuthService.getUserIdFromToken(token);

        if (username == null || userId == null) {
            sendError(ctx, "Invalid token data");
            ctx.close();
            return;
        }

        sessionManager.addSession(ctx.channel().id().asShortText(), ctx, username, userId);

        WebSocketMessage authOk = new WebSocketMessage();
        authOk.setType(MessageType.AUTH_SUCCESS);
        authOk.setContent("Authentication successful");
        authOk.setUserId(userId);
        authOk.setUsername(username);
        sendMessage(ctx, authOk);

        WebSocketMessage connected = new WebSocketMessage();
        connected.setType(MessageType.SYSTEM_MESSAGE);
        connected.setContent("CONNECTED");
        connected.setUserId(userId);
        connected.setUsername(username);
        sendMessage(ctx, connected);

        log.info("[AUTH] User {} (ID: {}) authenticated via URL token", username, userId);
    }

    private void sendMessage(ChannelHandlerContext ctx, WebSocketMessage message) {
        try {
            ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(message)));
        } catch (Exception e) {
            log.error("[WS] Error sending message: {}", e.getMessage());
        }
    }

    private void sendError(ChannelHandlerContext ctx, String error) {
        WebSocketMessage err = new WebSocketMessage();
        err.setType(MessageType.ERROR);
        err.setContent(error);
        sendMessage(ctx, err);
    }
}