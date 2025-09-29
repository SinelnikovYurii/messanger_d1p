package com.messenger.core.dto;

import com.messenger.core.model.Chat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatDto {
    private Long id;
    private String chatName;
    private Chat.ChatType chatType;
    private String chatDescription;
    private String chatAvatarUrl;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;
    private UserDto createdBy;
    private List<UserDto> participants;
    private MessageDto lastMessage;
    private Integer unreadCount;

    // DTO для создания нового чата
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateChatRequest {
        private String chatName;
        private Chat.ChatType chatType;
        private String chatDescription;
        private List<Long> participantIds;
    }

    // DTO для добавления пользователей в чат
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddParticipantsRequest {
        private Long chatId;
        private List<Long> userIds;
    }

    // DTO для создания приватного чата
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePrivateChatRequest {
        private Long participantId;
    }
}
