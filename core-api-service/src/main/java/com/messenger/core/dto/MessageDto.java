package com.messenger.core.dto;

import com.messenger.core.model.Message;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {
    private Long id;
    private String content;
    private Message.MessageType messageType;
    private Boolean isEdited;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UserDto sender;
    private Long chatId;
    private MessageDto replyToMessage;

    // Поля для файлов
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private String thumbnailUrl;

    // DTO для отправки нового сообщения
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendMessageRequest {
        private Long chatId;
        private String content;
        private Message.MessageType messageType = Message.MessageType.TEXT;
        private Long replyToMessageId; // ID сообщения, на которое отвечаем

        // Для файловых сообщений
        private String fileUrl;
        private String fileName;
        private Long fileSize;
        private String mimeType;
        private String thumbnailUrl;
    }

    // DTO для редактирования сообщения
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EditMessageRequest {
        private Long messageId;
        private String content;
    }

    // DTO для системных сообщений
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemMessageDto {
        private String content;
        private Message.MessageType messageType = Message.MessageType.SYSTEM;
        private LocalDateTime createdAt;
    }
}
