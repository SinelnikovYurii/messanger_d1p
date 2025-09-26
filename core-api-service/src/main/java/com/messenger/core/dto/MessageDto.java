package com.messenger.core.dto;

import com.messenger.core.model.Message;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageDto {
    private Long id;
    private String content;
    private Message.MessageType type;
    private LocalDateTime sentAt;
    private Long chatId;
    private Boolean isEdited;
    private LocalDateTime editedAt;
    private Message.MessageStatus status;
    private UserDto sender;
}
