package com.messenger.websocket.model;

public enum MessageType {
    JOIN_CHAT,
    LEAVE_CHAT,
    SEND_MESSAGE,
    CHAT_MESSAGE,
    MESSAGE_SENT,
    MESSAGE_READ,
    USER_ONLINE,
    USER_OFFLINE,
    AUTH,
    AUTH_SUCCESS,
    SYSTEM_MESSAGE,
    PING,
    PONG,
    ERROR,

    FRIEND_REQUEST_RECEIVED,  // Получен новый запрос в друзья
    FRIEND_REQUEST_ACCEPTED,  // Запрос в друзья принят
    FRIEND_REQUEST_REJECTED,  // Запрос в друзья отклонен

    // ===== WebRTC сигналинг =====
    CALL_OFFER,       // Инициатор отправляет SDP offer
    CALL_ANSWER,      // Ответ на звонок (SDP answer)
    ICE_CANDIDATE,    // ICE candidate для NAT traversal
    CALL_REJECT,      // Отклонить входящий звонок
    CALL_END,         // Завершить активный звонок
    CALL_BUSY,        // Пользователь занят (уже в звонке)
    CALL_MISSED,      // Звонок пропущен (нет ответа)
    CALL_RINGING      // Уведомление: у получателя звонит телефон
}
