package com.messenger.websocket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebSocketMessage {

    private Long id;
    private MessageType type;
    private String content;
    private String token;
    private Long chatId;
    private Long userId;
    private Long senderId;
    private String username;
    private String senderUsername;
    private LocalDateTime timestamp;

    // Поля для поддержки файлов
    private String messageType; // Тип сообщения как строка: TEXT, IMAGE, FILE
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private String thumbnailUrl;

    // Поля для поддержки MESSAGE_READ уведомлений
    private Long messageId; // ID прочитанного сообщения
    private Long readerId; // ID пользователя, который прочитал
    private String readerUsername; // Имя пользователя, который прочитал

    // Поле для онлайн-статуса
    private LocalDateTime lastSeen; // Время последнего визита (для USER_OFFLINE)
    private Boolean isOnline; // Статус онлайн (для USER_ONLINE/USER_OFFLINE)

    @JsonProperty("type")
    public MessageType getTypeEnum() {
        return type;
    }

    @JsonProperty("type")
    public void setTypeEnum(MessageType mt) {
        this.type = mt;
    }

    // Конструктор для быстрого создания сообщений
    public WebSocketMessage(MessageType type, String content) {
        this.type = type;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    // Конструктор для чат сообщений
    public WebSocketMessage(MessageType type, String content, Long chatId, Long userId, String username) {
        this.type = type;
        this.content = content;
        this.chatId = chatId;
        this.userId = userId;
        this.username = username;
        this.timestamp = LocalDateTime.now();
    }
}
