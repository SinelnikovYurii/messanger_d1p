package com.messenger.gateway.model.DTO;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChatDto {
    private Long id;
    private String name;
    private Boolean isGroup;
    private LocalDateTime createdAt;
    private List<UserDto> participants;
}
