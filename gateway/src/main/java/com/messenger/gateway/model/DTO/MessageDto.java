package com.messenger.gateway.model.DTO;

import lombok.Data;
import java.time.LocalDateTime;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MessageDto {
    private Long id;
    private Long senderId;
    private Long chatId;
    private String content;
    private LocalDateTime timestamp;
    private Boolean isRead = false;
}
