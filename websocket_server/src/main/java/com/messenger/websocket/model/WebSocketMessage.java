package com.messenger.websocket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
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
    private String messageType;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private String thumbnailUrl;

    // Поля для поддержки MESSAGE_READ уведомлений
    private Long messageId;
    private Long readerId;
    private String readerUsername;

    // Поле для онлайн-статуса
    private LocalDateTime lastSeen;
    private Boolean isOnline;

    // ===== Поля WebRTC сигналинга =====
    private String callId;
    private Long targetUserId;
    private String sdp;
    private String sdpType;
    private String candidate;
    private String sdpMid;
    private Integer sdpMLineIndex;
    private String callType;

    @JsonProperty("type")
    public MessageType getTypeEnum() {
        return type;
    }

    @JsonProperty("type")
    public void setTypeEnum(MessageType mt) {
        this.type = mt;
    }

    /** Конструктор для быстрого создания сообщений (2 аргумента) */
    public WebSocketMessage(MessageType type, String content) {
        this.type = type;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    /** Конструктор для чат-сообщений (5 аргументов) */
    public WebSocketMessage(MessageType type, String content, Long chatId, Long userId, String username) {
        this.type = type;
        this.content = content;
        this.chatId = chatId;
        this.userId = userId;
        this.username = username;
        this.timestamp = LocalDateTime.now();
    }

    /** Конструктор без WebRTC-полей — 21 аргумент (используется в тестах) */
    public WebSocketMessage(Long id, MessageType type, String content, String token,
                            Long chatId, Long userId, Long senderId,
                            String username, String senderUsername,
                            LocalDateTime timestamp,
                            String messageType, String fileUrl, String fileName,
                            Long fileSize, String mimeType, String thumbnailUrl,
                            Long messageId, Long readerId, String readerUsername,
                            LocalDateTime lastSeen, Boolean isOnline) {
        this.id = id;
        this.type = type;
        this.content = content;
        this.token = token;
        this.chatId = chatId;
        this.userId = userId;
        this.senderId = senderId;
        this.username = username;
        this.senderUsername = senderUsername;
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.fileUrl = fileUrl;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.thumbnailUrl = thumbnailUrl;
        this.messageId = messageId;
        this.readerId = readerId;
        this.readerUsername = readerUsername;
        this.lastSeen = lastSeen;
        this.isOnline = isOnline;
    }

    /** Полный конструктор со всеми полями включая WebRTC — 29 аргументов */
    public WebSocketMessage(Long id, MessageType type, String content, String token,
                            Long chatId, Long userId, Long senderId,
                            String username, String senderUsername,
                            LocalDateTime timestamp,
                            String messageType, String fileUrl, String fileName,
                            Long fileSize, String mimeType, String thumbnailUrl,
                            Long messageId, Long readerId, String readerUsername,
                            LocalDateTime lastSeen, Boolean isOnline,
                            String callId, Long targetUserId,
                            String sdp, String sdpType, String candidate,
                            String sdpMid, Integer sdpMLineIndex, String callType) {
        this(id, type, content, token, chatId, userId, senderId, username, senderUsername,
             timestamp, messageType, fileUrl, fileName, fileSize, mimeType, thumbnailUrl,
             messageId, readerId, readerUsername, lastSeen, isOnline);
        this.callId = callId;
        this.targetUserId = targetUserId;
        this.sdp = sdp;
        this.sdpType = sdpType;
        this.candidate = candidate;
        this.sdpMid = sdpMid;
        this.sdpMLineIndex = sdpMLineIndex;
        this.callType = callType;
    }
}

