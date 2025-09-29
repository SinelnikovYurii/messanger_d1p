package com.messenger.core.service;

import com.messenger.core.dto.UserDto;
import com.messenger.core.model.Friendship;
import com.messenger.core.model.User;
import com.messenger.core.repository.FriendshipRepository;
import com.messenger.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    /**
     * Поиск пользователей по запросу
     */
    @Transactional(readOnly = true)
    public List<UserDto.UserSearchResult> searchUsers(String query, Long currentUserId) {
        List<User> users = userRepository.searchUsers(query);

        return users.stream()
            .filter(user -> !user.getId().equals(currentUserId)) // Исключаем текущего пользователя
            .map(user -> convertToSearchResult(user, currentUserId))
            .collect(Collectors.toList());
    }

    /**
     * Получить информацию о пользователе
     */
    @Transactional(readOnly = true)
    public UserDto getUserInfo(Long userId, Long currentUserId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        UserDto dto = convertToDto(user);

        // Добавляем информацию о статусе дружбы
        if (!userId.equals(currentUserId)) {
            Optional<Friendship.FriendshipStatus> status =
                friendshipRepository.getFriendshipStatus(currentUserId, userId);
            dto.setFriendshipStatus(status.orElse(null));
        }

        return dto;
    }

    /**
     * Обновить статус онлайн
     */
    public void updateOnlineStatus(Long userId, boolean isOnline) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        user.setIsOnline(isOnline);
        if (!isOnline) {
            user.setLastSeen(LocalDateTime.now());
        }

        userRepository.save(user);
    }

    /**
     * Обновить профиль пользователя
     */
    public UserDto updateProfile(Long userId, UserDto.UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getProfilePictureUrl() != null) {
            user.setProfilePictureUrl(request.getProfilePictureUrl());
        }

        User savedUser = userRepository.save(user);
        return convertToDto(savedUser);
    }

    /**
     * Получить пользователя по username
     */
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Получить всех пользователей кроме текущего
     */
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers(Long currentUserId) {
        List<User> users = userRepository.findAll();
        return users.stream()
            .filter(user -> !user.getId().equals(currentUserId))
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * Конвертировать User в UserDto
     */
    public UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setProfilePictureUrl(user.getProfilePictureUrl());
        dto.setIsOnline(user.getIsOnline());
        dto.setLastSeen(user.getLastSeen());
        return dto;
    }

    /**
     * Конвертировать User в UserSearchResult с информацией о дружбе
     */
    private UserDto.UserSearchResult convertToSearchResult(User user, Long currentUserId) {
        UserDto.UserSearchResult result = new UserDto.UserSearchResult();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setFirstName(user.getFirstName());
        result.setLastName(user.getLastName());
        result.setProfilePictureUrl(user.getProfilePictureUrl());
        result.setIsOnline(user.getIsOnline());

        // Получаем статус дружбы
        Optional<Friendship.FriendshipStatus> friendshipStatus =
            friendshipRepository.getFriendshipStatus(currentUserId, user.getId());
        result.setFriendshipStatus(friendshipStatus.orElse(null));

        // Определяем, можно ли начать чат (если пользователи друзья или нет связи)
        result.setCanStartChat(
            friendshipStatus.isEmpty() ||
            friendshipStatus.get() == Friendship.FriendshipStatus.ACCEPTED
        );

        return result;
    }
}
