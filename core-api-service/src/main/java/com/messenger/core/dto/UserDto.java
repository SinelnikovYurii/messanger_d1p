package com.messenger.core.dto;

import com.messenger.core.model.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String avatarUrl;
    private String bio;
    private User.UserStatus status;
    private LocalDateTime lastSeen;
    private LocalDateTime createdAt;

    // Недостающие поля для онлайн статуса
    private Boolean isOnline;
    private LocalDateTime lastSeenAt;
}
