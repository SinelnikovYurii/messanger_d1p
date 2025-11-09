package websocket.model;

public enum MessageType {
    JOIN_CHAT,
    LEAVE_CHAT,
    SEND_MESSAGE,
    CHAT_MESSAGE,
    MESSAGE_SENT,
    MESSAGE_READ,         // Новый тип для уведомлений о прочтении
    USER_ONLINE,
    USER_OFFLINE,
    AUTH,
    AUTH_SUCCESS,
    SYSTEM_MESSAGE,
    PING,
    PONG,
    ERROR,
    // Новые типы для запросов в друзья
    FRIEND_REQUEST_RECEIVED,  // Получен новый запрос в друзья
    FRIEND_REQUEST_ACCEPTED,  // Запрос в друзья принят
    FRIEND_REQUEST_REJECTED   // Запрос в друзья отклонен
}
