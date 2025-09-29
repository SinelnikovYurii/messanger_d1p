package com.messenger.core.dto;

import com.messenger.core.model.Friendship;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String profilePictureUrl;
    private Boolean isOnline;
    private LocalDateTime lastSeen;
    private Friendship.FriendshipStatus friendshipStatus; // Статус дружбы с текущим пользователем

    // DTO для поиска пользователей
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSearchResult {
        private Long id;
        private String username;
        private String firstName;
        private String lastName;
        private String profilePictureUrl;
        private Boolean isOnline;
        private Friendship.FriendshipStatus friendshipStatus;
        private Boolean canStartChat; // Можно ли начать чат с этим пользователем
    }

    // DTO для отправки запроса дружбы
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FriendRequest {
        private Long userId;
    }

    // DTO для ответа на запрос дружбы
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FriendRequestResponse {
        private Long requestId;
        private Boolean accept; // true - принять, false - отклонить
    }

    // DTO для обновления профиля
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateProfileRequest {
        private String firstName;
        private String lastName;
        private String profilePictureUrl;
    }
}
