package websocket.model;

public enum MessageType {
    JOIN_CHAT,
    LEAVE_CHAT,
    SEND_MESSAGE,
    CHAT_MESSAGE,
    MESSAGE_SENT,
    USER_ONLINE,
    USER_OFFLINE,
    AUTH,
    AUTH_SUCCESS,
    SYSTEM_MESSAGE,
    PING,
    PONG,
    ERROR
}
