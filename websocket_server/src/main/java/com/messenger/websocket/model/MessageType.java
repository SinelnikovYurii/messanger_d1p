package websocket.model;

public enum MessageType {
    JOIN_CHAT,
    LEAVE_CHAT,
    SEND_MESSAGE,
    CHAT_MESSAGE,
    USER_ONLINE,
    USER_OFFLINE,
    ERROR
}
