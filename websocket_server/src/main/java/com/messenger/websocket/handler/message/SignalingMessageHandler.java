package com.messenger.websocket.handler.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.websocket.model.MessageType;
import com.messenger.websocket.model.WebSocketMessage;
import com.messenger.websocket.service.CallSessionManager;
import com.messenger.websocket.service.SessionManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Обрабатывает все WebRTC сигналинговые сообщения:
 * CALL_OFFER, CALL_ANSWER, ICE_CANDIDATE, CALL_REJECT, CALL_END, CALL_BUSY
 */
@Slf4j
@RequiredArgsConstructor
public class SignalingMessageHandler implements MessageHandler {

    private final SessionManager sessionManager;
    private final CallSessionManager callSessionManager;
    private final ObjectMapper objectMapper;

    @Override
    public void handle(ChannelHandlerContext ctx, WebSocketMessage message) {
        String sessionId = ctx.channel().id().asShortText();
        if (!sessionManager.isAuthenticated(sessionId)) {
            sendError(ctx, "Not authenticated");
            return;
        }

        Long callerId = sessionManager.getUserId(sessionId);
        String callerName = sessionManager.getUsername(sessionId);

        MessageType type = message.getType();
        log.info("[SIGNAL] {} from user {} ({}), callId: {}, target: {}",
                type, callerName, callerId, message.getCallId(), message.getTargetUserId());

        switch (type) {
            case CALL_OFFER   -> handleOffer(ctx, message, callerId, callerName);
            case CALL_ANSWER  -> handleAnswer(message, callerId);
            case ICE_CANDIDATE -> handleIceCandidate(message, callerId);
            case CALL_REJECT  -> handleReject(message, callerId);
            case CALL_END     -> handleEnd(message, callerId);
            default -> log.warn("[SIGNAL] Unexpected signaling type: {}", type);
        }
    }

    // ─── OFFER ────────────────────────────────────────────────────────────────

    private void handleOffer(ChannelHandlerContext ctx, WebSocketMessage message,
                              Long callerId, String callerName) {
        Long targetUserId = message.getTargetUserId();
        if (targetUserId == null) {
            sendError(ctx, "targetUserId is required for CALL_OFFER");
            return;
        }

        // Проверяем: не занят ли адресат
        if (callSessionManager.isUserInCall(targetUserId)) {
            WebSocketMessage busy = new WebSocketMessage();
            busy.setType(MessageType.CALL_BUSY);
            busy.setCallId(message.getCallId());
            busy.setUserId(targetUserId);
            send(ctx, busy);
            log.info("[SIGNAL] User {} is busy, sending CALL_BUSY to caller {}", targetUserId, callerId);
            return;
        }

        // Регистрируем сессию звонка
        String callId = callSessionManager.createCall(message.getCallId(), callerId, targetUserId);
        message.setCallId(callId);

        // Проставляем данные звонящего, чтобы адресат знал кто звонит
        message.setUserId(callerId);
        message.setUsername(callerName);

        // Пересылаем OFFER адресату
        boolean delivered = sessionManager.sendMessageToUser(targetUserId, message);
        if (!delivered) {
            // Адресат офлайн
            log.warn("[SIGNAL] Target user {} is offline, offer not delivered", targetUserId);
            callSessionManager.endCall(callId);
            WebSocketMessage missed = new WebSocketMessage();
            missed.setType(MessageType.CALL_MISSED);
            missed.setCallId(callId);
            missed.setUserId(targetUserId);
            send(ctx, missed);
        } else {
            log.info("[SIGNAL] CALL_OFFER forwarded, callId: {}", callId);
        }
    }

    // ─── ANSWER ───────────────────────────────────────────────────────────────

    private void handleAnswer(WebSocketMessage message, Long answererId) {
        String callId = message.getCallId();
        if (callId == null) {
            log.warn("[SIGNAL] CALL_ANSWER missing callId");
            return;
        }
        Long callerId = callSessionManager.getCallerId(callId);
        if (callerId == null) {
            log.warn("[SIGNAL] CALL_ANSWER: unknown callId {}", callId);
            return;
        }

        callSessionManager.activateCall(callId);
        message.setUserId(answererId);
        sessionManager.sendMessageToUser(callerId, message);
        log.info("[SIGNAL] CALL_ANSWER forwarded to caller {}, callId: {}", callerId, callId);
    }

    // ─── ICE CANDIDATE ────────────────────────────────────────────────────────

    private void handleIceCandidate(WebSocketMessage message, Long senderId) {
        Long targetUserId = message.getTargetUserId();
        if (targetUserId == null) {
            log.warn("[SIGNAL] ICE_CANDIDATE missing targetUserId");
            return;
        }
        message.setUserId(senderId);
        sessionManager.sendMessageToUser(targetUserId, message);
        log.debug("[SIGNAL] ICE_CANDIDATE forwarded from {} to {}", senderId, targetUserId);
    }

    // ─── REJECT ───────────────────────────────────────────────────────────────

    private void handleReject(WebSocketMessage message, Long rejecterId) {
        String callId = message.getCallId();
        if (callId == null) return;

        Long callerId = callSessionManager.getCallerId(callId);
        callSessionManager.endCall(callId);

        if (callerId != null) {
            message.setUserId(rejecterId);
            sessionManager.sendMessageToUser(callerId, message);
            log.info("[SIGNAL] CALL_REJECT forwarded to caller {}, callId: {}", callerId, callId);
        }
    }

    // ─── END ──────────────────────────────────────────────────────────────────

    private void handleEnd(WebSocketMessage message, Long enderId) {
        String callId = message.getCallId();
        if (callId == null) return;

        Long peerId = callSessionManager.getPeerId(callId, enderId);
        callSessionManager.endCall(callId);

        if (peerId != null) {
            message.setUserId(enderId);
            sessionManager.sendMessageToUser(peerId, message);
            log.info("[SIGNAL] CALL_END forwarded to peer {}, callId: {}", peerId, callId);
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void send(ChannelHandlerContext ctx, WebSocketMessage msg) {
        try {
            ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(msg)));
        } catch (Exception e) {
            log.error("[SIGNAL] Error sending message: {}", e.getMessage());
        }
    }

    private void sendError(ChannelHandlerContext ctx, String error) {
        WebSocketMessage err = new WebSocketMessage();
        err.setType(MessageType.ERROR);
        err.setContent(error);
        send(ctx, err);
    }
}
