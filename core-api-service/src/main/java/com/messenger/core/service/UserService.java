package com.messenger.core.service;

import com.messenger.core.dto.UserDto;
import com.messenger.core.model.Friendship;
import com.messenger.core.model.User;
import com.messenger.core.repository.FriendshipRepository;
import com.messenger.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

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
            Optional<Friendship.FriendshipStatus> status = getFriendshipStatus(currentUserId, userId);
            dto.setFriendshipStatus(status.orElse(null));
        }

        return dto;
    }

    /**
     * Обновить статус онлайн
     */
    @CacheEvict(value = "users", key = "#userId")
    public void updateOnlineStatus(Long userId, boolean isOnline) {
        log.info("[USER-SERVICE] ========================================");
        log.info("[USER-SERVICE] updateOnlineStatus called: userId={}, isOnline={}", userId, isOnline);
        log.info("[USER-SERVICE] Cache evicted for userId={}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        log.info("[USER-SERVICE] Found user: id={}, username={}, currentOnline={}, currentLastSeen={}",
            user.getId(), user.getUsername(), user.getIsOnline(), user.getLastSeen());

        user.setIsOnline(isOnline);
        if (!isOnline) {
            // Пользователь уходит в оффлайн - устанавливаем время последнего визита
            LocalDateTime now = LocalDateTime.now();
            user.setLastSeen(now);
            log.info("[USER-SERVICE] Setting user OFFLINE, lastSeen={}", now);
        } else {
            // ИСПРАВЛЕНИЕ: Пользователь приходит в онлайн - очищаем lastSeen
            user.setLastSeen(null);
            log.info("[USER-SERVICE] Setting user ONLINE, clearing lastSeen");
        }

        User savedUser = userRepository.save(user);
        log.info("[USER-SERVICE] User saved: id={}, isOnline={}, lastSeen={}",
            savedUser.getId(), savedUser.getIsOnline(), savedUser.getLastSeen());
        log.info("[USER-SERVICE] ========================================");
    }

    /**
     * Обновить профиль пользователя
     */
    public UserDto updateProfile(Long userId, UserDto.UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        // Проверяем уникальность username, если он изменяется
        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            Optional<User> existingUser = userRepository.findByUsername(request.getUsername());
            if (existingUser.isPresent()) {
                throw new RuntimeException("Пользователь с таким именем уже существует");
            }
            user.setUsername(request.getUsername());
        }

        // Проверяем уникальность email, если он изменяется
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            Optional<User> existingUser = userRepository.findByEmail(request.getEmail());
            if (existingUser.isPresent()) {
                throw new RuntimeException("Пользователь с таким email уже существует");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getProfilePictureUrl() != null) {
            user.setProfilePictureUrl(request.getProfilePictureUrl());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        User savedUser = userRepository.save(user);
        return convertToDto(savedUser);
    }

    /**
     * Загрузить аватар пользователя
     */
    public String uploadAvatar(Long userId, MultipartFile file) throws IOException {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        // Создаем директорию для аватаров, если она не существует
        Path avatarDir = Paths.get(uploadDir, "avatars");
        if (!Files.exists(avatarDir)) {
            Files.createDirectories(avatarDir);
        }

        // Генерируем уникальное имя файла
        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename != null && originalFilename.contains(".")
            ? originalFilename.substring(originalFilename.lastIndexOf("."))
            : ".jpg";
        String fileName = "avatar_" + userId + "_" + UUID.randomUUID() + fileExtension;

        // Сохраняем файл
        Path targetPath = avatarDir.resolve(fileName);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // Формируем URL для доступа к аватару
        String avatarUrl = "/avatars/" + fileName;

        // Обновляем профиль пользователя
        user.setProfilePictureUrl(avatarUrl);
        userRepository.save(user);

        return avatarUrl;
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
     * Получить список друзей пользователя
     */
    @Transactional(readOnly = true)
    public List<UserDto> getFriends(Long userId) {
        List<Friendship> friendships = friendshipRepository.findAcceptedFriendships(userId);

        return friendships.stream()
            .map(friendship -> {
                // Определяем, кто является другом текущего пользователя
                User friend = friendship.getRequester().getId().equals(userId)
                    ? friendship.getReceiver()
                    : friendship.getRequester();

                return convertToDto(friend);
            })
            .collect(Collectors.toList());
    }

    /**
     * Получить статус дружбы с кешированием
     */
    @Cacheable(value = "friendshipStatus", key = "#currentUserId + '-' + #targetUserId")
    @Transactional(readOnly = true)
    public Optional<Friendship.FriendshipStatus> getFriendshipStatus(Long currentUserId, Long targetUserId) {
        return friendshipRepository.getFriendshipStatus(currentUserId, targetUserId);
    }

    /**
     * Конвертировать User в UserDto
     */
    @Cacheable(value = "users", key = "#user.id", unless = "#result == null")
    public UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setProfilePictureUrl(user.getProfilePictureUrl());
        dto.setBio(user.getBio());
        dto.setIsOnline(user.getIsOnline());
        dto.setLastSeen(user.getLastSeen());
        return dto;
    }

    /**
     * Конвертировать User в UserSearchResult с информацией о дружбе
     */
    private UserDto.UserSearchResult convertToSearchResult(User user, Long currentUserId) {
        log.info("Converting user to search result: id={}, username={}, profilePictureUrl={}",
            user.getId(), user.getUsername(), user.getProfilePictureUrl());

        UserDto.UserSearchResult result = new UserDto.UserSearchResult();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setFirstName(user.getFirstName());
        result.setLastName(user.getLastName());
        result.setProfilePictureUrl(user.getProfilePictureUrl());
        result.setIsOnline(user.getIsOnline());

        log.info("Search result created with profilePictureUrl: {}", result.getProfilePictureUrl());

        // Получаем статус дружбы с кешированием
        Optional<Friendship.FriendshipStatus> friendshipStatus = getFriendshipStatus(currentUserId, user.getId());
        result.setFriendshipStatus(friendshipStatus.orElse(null));

        // Определяем, можно ли начать чат (если пользователи друзья или нет связи)
        result.setCanStartChat(
            friendshipStatus.isEmpty() ||
            friendshipStatus.get() == Friendship.FriendshipStatus.ACCEPTED
        );

        return result;
    }
}
