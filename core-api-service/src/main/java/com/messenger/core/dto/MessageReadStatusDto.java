package com.messenger.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageReadStatusDto {
    private Long id;
    private Long messageId;
    private UserDto user;
    private LocalDateTime readAt;
}

