package com.messenger.core.dto;

import com.messenger.core.model.Chat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChatDto {
    private Long id;
    private String name;
    private String description;
    private Chat.ChatType type;
    private String avatarUrl;
    private LocalDateTime createdAt;
    private Long createdBy;
    private List<UserDto> participants;
    private MessageDto lastMessage;
    private Long unreadCount;
    private LocalDateTime lastActivity;
}
