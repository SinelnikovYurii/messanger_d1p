package com.messenger.websocket.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Управляет жизненным циклом WebRTC звонков.
 *
 * Состояния звонка:
 *   RINGING  → ждём ответа (таймаут RING_TIMEOUT_SEC)
 *   ACTIVE   → звонок принят и идёт
 *   ENDED    → завершён (удаляется из карты через ENDED_TTL_SEC)
 */
@Slf4j
@Service
public class CallSessionManager {

    private static final long RING_TIMEOUT_SEC = 30;   // секунд ждём ответа
    private static final long ENDED_TTL_SEC    = 10;   // секунд держим запись после завершения

    public enum CallState { RINGING, ACTIVE, ENDED }

    @Getter
    public static class CallSession {
        private final String callId;
        private final Long callerId;
        private final Long calleeId;
        private volatile CallState state;
        private final LocalDateTime createdAt;

        public CallSession(String callId, Long callerId, Long calleeId) {
            this.callId    = callId;
            this.callerId  = callerId;
            this.calleeId  = calleeId;
            this.state     = CallState.RINGING;
            this.createdAt = LocalDateTime.now();
        }

        public void activate() { this.state = CallState.ACTIVE; }
        public void end()      { this.state = CallState.ENDED;  }
    }

    /** callId → сессия */
    private final ConcurrentHashMap<String, CallSession> calls = new ConcurrentHashMap<>();

    /** userId → callId (для быстрой проверки "занят ли пользователь") */
    private final ConcurrentHashMap<Long, String> userInCall = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "call-session-cleanup");
                t.setDaemon(true);
                return t;
            });

    private final SessionManager sessionManager;

    public CallSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Создаёт новый звонок. Если callId == null — генерирует UUID.
     * @return финальный callId
     */
    public String createCall(String proposedCallId, Long callerId, Long calleeId) {
        String callId = (proposedCallId != null && !proposedCallId.isBlank())
                ? proposedCallId
                : UUID.randomUUID().toString();

        CallSession session = new CallSession(callId, callerId, calleeId);
        calls.put(callId, session);
        userInCall.put(callerId, callId);
        userInCall.put(calleeId, callId);

        log.info("[CALL] Created call {} — caller: {}, callee: {}", callId, callerId, calleeId);

        // Таймер на случай если не ответят
        scheduler.schedule(() -> handleRingTimeout(callId), RING_TIMEOUT_SEC, TimeUnit.SECONDS);

        return callId;
    }

    /**
     * Переводит звонок в состояние ACTIVE (принят).
     */
    public void activateCall(String callId) {
        CallSession session = calls.get(callId);
        if (session != null && session.getState() == CallState.RINGING) {
            session.activate();
            log.info("[CALL] Call {} is now ACTIVE", callId);
        }
    }

    /**
     * Завершает звонок, убирает пользователей из userInCall.
     */
    public void endCall(String callId) {
        CallSession session = calls.get(callId);
        if (session == null) return;

        session.end();
        userInCall.remove(session.getCallerId());
        userInCall.remove(session.getCalleeId());

        log.info("[CALL] Call {} ENDED (was {})", callId, session.getState());

        // Удаляем запись через небольшой TTL
        scheduler.schedule(() -> calls.remove(callId), ENDED_TTL_SEC, TimeUnit.SECONDS);
    }

    /**
     * Проверяет, находится ли пользователь в активном или рингующем звонке.
     */
    public boolean isUserInCall(Long userId) {
        String callId = userInCall.get(userId);
        if (callId == null) return false;
        CallSession session = calls.get(callId);
        return session != null && session.getState() != CallState.ENDED;
    }

    /**
     * Возвращает ID инициатора звонка.
     */
    public Long getCallerId(String callId) {
        CallSession session = calls.get(callId);
        return session != null ? session.getCallerId() : null;
    }

    /**
     * Возвращает ID «другой стороны» (не того, кто вызывает метод).
     */
    public Long getPeerId(String callId, Long selfId) {
        CallSession session = calls.get(callId);
        if (session == null) return null;
        if (session.getCallerId().equals(selfId)) return session.getCalleeId();
        if (session.getCalleeId().equals(selfId)) return session.getCallerId();
        return null;
    }

    public CallSession getSession(String callId) {
        return calls.get(callId);
    }

    // ──────────────────────────────────────────────────────────────────────────

    private void handleRingTimeout(String callId) {
        CallSession session = calls.get(callId);
        if (session == null || session.getState() != CallState.RINGING) return;

        log.info("[CALL] Ring timeout for call {}, sending CALL_MISSED", callId);
        endCall(callId);

        // Уведомляем инициатора о пропущенном звонке
        com.messenger.websocket.model.WebSocketMessage missed =
                new com.messenger.websocket.model.WebSocketMessage();
        missed.setType(com.messenger.websocket.model.MessageType.CALL_MISSED);
        missed.setCallId(callId);
        missed.setUserId(session.getCalleeId());
        sessionManager.sendMessageToUser(session.getCallerId(), missed);

        // Уведомляем адресата о пропущенном звонке (чтобы показать в UI)
        com.messenger.websocket.model.WebSocketMessage missedCallee =
                new com.messenger.websocket.model.WebSocketMessage();
        missedCallee.setType(com.messenger.websocket.model.MessageType.CALL_MISSED);
        missedCallee.setCallId(callId);
        missedCallee.setUserId(session.getCallerId());
        sessionManager.sendMessageToUser(session.getCalleeId(), missedCallee);
    }
}
