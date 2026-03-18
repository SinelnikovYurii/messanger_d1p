package com.messenger.core.service.user;

import com.messenger.core.dto.UserDto;
import com.messenger.core.model.Friendship;
import com.messenger.core.model.User;
import com.messenger.core.repository.FriendshipRepository;
import com.messenger.core.repository.UserRepository;
import com.messenger.core.service.encryption.EncryptionService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
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

/**
 * Реализация {@link UserService} — управление пользователями, профилями и друзьями.
 * <p>
 * Криптографические операции (публичные ключи, prekey bundle, backup ключей) вынесены
 * в {@link EncryptionService} согласно принципу единственной ответственности (SRP).
 */
@Service
@Transactional
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    /**
     * Self-инъекция через {@code @Lazy} для корректной работы {@code @Cacheable}
     * при внутренних вызовах методов класса (self-invocation).
     * Без этого Spring AOP-прокси не перехватывает вызовы внутри того же бина.
     */
    @Setter(onMethod_ = {@Autowired, @Lazy})
    private UserService self;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    public UserServiceImpl(UserRepository userRepository, FriendshipRepository friendshipRepository) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
    }

    /**
     * Поиск пользователей по строке запроса.
     * Текущий пользователь исключается из результатов.
     *
     * @param query         поисковая строка (имя пользователя, email и т.д.)
     * @param currentUserId ID вызывающего пользователя
     * @return список найденных пользователей
     */
    @Transactional(readOnly = true)
    public List<UserDto.UserSearchResult> searchUsers(String query, Long currentUserId) {
        List<User> users = userRepository.searchUsers(query);

        return users.stream()
            .filter(user -> !user.getId().equals(currentUserId))
            .map(user -> convertToSearchResult(user, currentUserId))
            .collect(Collectors.toList());
    }

    /**
     * Получить полную информацию о пользователе с учётом статуса дружбы
     * относительно вызывающего пользователя.
     *
     * @param userId        ID запрашиваемого пользователя
     * @param currentUserId ID вызывающего пользователя
     * @return DTO с данными пользователя и статусом дружбы
     */
    @Transactional(readOnly = true)
    public UserDto getUserInfo(Long userId, Long currentUserId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        UserDto dto = self.convertToDto(user);

        if (!userId.equals(currentUserId)) {
            Optional<Friendship.FriendshipStatus> status = self.getFriendshipStatus(currentUserId, userId);
            dto.setFriendshipStatus(status.orElse(null));
        }

        return dto;
    }

    /**
     * Обновить статус онлайн-присутствия пользователя.
     * При уходе в офлайн фиксируется время последнего посещения ({@code lastSeen}).
     * При входе в онлайн {@code lastSeen} сбрасывается в {@code null}.
     * Вызов инвалидирует кеш пользователя.
     *
     * @param userId   ID пользователя
     * @param isOnline {@code true} — онлайн, {@code false} — офлайн
     */
    @CacheEvict(value = "users", key = "#userId")
    public void updateOnlineStatus(Long userId, boolean isOnline) {
        log.info("[USER-SERVICE] updateOnlineStatus: userId={}, isOnline={}", userId, isOnline);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        user.setIsOnline(isOnline);
        if (!isOnline) {
            LocalDateTime now = LocalDateTime.now();
            user.setLastSeen(now);
            log.info("[USER-SERVICE] Пользователь {} перешёл в офлайн, lastSeen={}", userId, now);
        } else {
            user.setLastSeen(null);
            log.info("[USER-SERVICE] Пользователь {} перешёл в онлайн", userId);
        }

        userRepository.save(user);
    }

    /**
     * Обновить данные профиля пользователя.
     * Уникальность username и email проверяется перед сохранением.
     *
     * @param userId  ID пользователя
     * @param request объект с новыми данными профиля
     * @return обновлённый DTO пользователя
     * @throws RuntimeException если username или email уже заняты другим пользователем
     */
    public UserDto updateProfile(Long userId, UserDto.UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            Optional<User> existingUser = userRepository.findByUsername(request.getUsername());
            if (existingUser.isPresent()) {
                throw new RuntimeException("Пользователь с таким именем уже существует");
            }
            user.setUsername(request.getUsername());
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            Optional<User> existingUser = userRepository.findByEmail(request.getEmail());
            if (existingUser.isPresent()) {
                throw new RuntimeException("Пользователь с таким email уже существует");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null)  user.setLastName(request.getLastName());
        if (request.getProfilePictureUrl() != null) user.setProfilePictureUrl(request.getProfilePictureUrl());
        if (request.getBio() != null) user.setBio(request.getBio());

        return self.convertToDto(userRepository.save(user));
    }

    /**
     * Загрузить аватар пользователя на диск и обновить ссылку в профиле.
     * Файл сохраняется в директорию {@code {uploadDir}/avatars/} под уникальным именем.
     *
     * @param userId ID пользователя
     * @param file   загружаемый файл изображения
     * @return URL загруженного аватара
     * @throws IOException если произошла ошибка ввода-вывода при сохранении файла
     */
    public String uploadAvatar(Long userId, MultipartFile file) throws IOException {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        Path avatarDir = Paths.get(uploadDir, "avatars");
        if (!Files.exists(avatarDir)) {
            Files.createDirectories(avatarDir);
        }

        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename != null && originalFilename.contains(".")
            ? originalFilename.substring(originalFilename.lastIndexOf("."))
            : ".jpg";
        String fileName = "avatar_" + userId + "_" + UUID.randomUUID() + fileExtension;

        Files.copy(file.getInputStream(), avatarDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

        String avatarUrl = "/avatars/" + fileName;
        user.setProfilePictureUrl(avatarUrl);
        userRepository.save(user);

        return avatarUrl;
    }

    /**
     * Найти пользователя по имени пользователя (username).
     *
     * @param username имя пользователя
     * @return {@link Optional} с найденным пользователем или пустой, если не найден
     */
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Получить список всех пользователей, кроме текущего.
     *
     * @param currentUserId ID вызывающего пользователя
     * @return список DTO пользователей
     */
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers(Long currentUserId) {
        return userRepository.findAll().stream()
            .filter(user -> !user.getId().equals(currentUserId))
            .map(user -> self.convertToDto(user))
            .collect(Collectors.toList());
    }

    /**
     * Получить список друзей пользователя (принятые заявки дружбы).
     *
     * @param userId ID пользователя
     * @return список DTO друзей
     */
    @Transactional(readOnly = true)
    public List<UserDto> getFriends(Long userId) {
        List<Friendship> friendships = friendshipRepository.findAcceptedFriendships(userId);

        return friendships.stream()
            .map(friendship -> {
                // Определяем, кто является другом относительно текущего пользователя
                User friend = friendship.getRequester().getId().equals(userId)
                    ? friendship.getReceiver()
                    : friendship.getRequester();
                return self.convertToDto(friend);
            })
            .collect(Collectors.toList());
    }

    /**
     * Получить статус дружбы между двумя пользователями.
     * Результат кешируется по паре {@code currentUserId-targetUserId}.
     *
     * @param currentUserId ID вызывающего пользователя
     * @param targetUserId  ID целевого пользователя
     * @return {@link Optional} со статусом дружбы или пустой, если связи нет
     */
    @Cacheable(value = "friendshipStatus", key = "#currentUserId + '-' + #targetUserId")
    @Transactional(readOnly = true)
    public Optional<Friendship.FriendshipStatus> getFriendshipStatus(Long currentUserId, Long targetUserId) {
        return friendshipRepository.getFriendshipStatus(currentUserId, targetUserId);
    }

    /**
     * Конвертировать сущность {@link User} в {@link UserDto}.
     * Результат кешируется по ID пользователя.
     *
     * @param user сущность пользователя
     * @return DTO пользователя
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
     * Найти пользователя по ID.
     *
     * @param id ID пользователя
     * @return {@link Optional} с найденным пользователем или пустой, если не найден
     */
    public Optional<User> findUserById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Сменить пароль пользователя.
     * Предполагается, что {@code newPassword} передаётся уже в захешированном виде
     * (хеширование выполняется на уровне контроллера или вызывающего сервиса).
     *
     * @param userId      ID пользователя
     * @param newPassword новый пароль (должен быть предварительно захеширован)
     * @throws RuntimeException если пользователь не найден
     */
    public void changePassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        user.setPassword(newPassword);
        userRepository.save(user);
        log.info("[USER-SERVICE] Пароль изменён для пользователя {}", userId);
    }

    /**
     * Конвертировать сущность {@link User} в {@link UserDto.UserSearchResult}.
     * Заполняет статус дружбы и флаг возможности начать чат.
     *
     * @param user          сущность пользователя
     * @param currentUserId ID вызывающего пользователя
     * @return результат поиска с информацией о дружбе
     */
    private UserDto.UserSearchResult convertToSearchResult(User user, Long currentUserId) {
        UserDto.UserSearchResult result = new UserDto.UserSearchResult();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setFirstName(user.getFirstName());
        result.setLastName(user.getLastName());
        result.setProfilePictureUrl(user.getProfilePictureUrl());
        result.setIsOnline(user.getIsOnline());

        Optional<Friendship.FriendshipStatus> friendshipStatus =
            self.getFriendshipStatus(currentUserId, user.getId());
        result.setFriendshipStatus(friendshipStatus.orElse(null));

        // Чат можно начать, если пользователи уже друзья или связи дружбы нет вовсе
        result.setCanStartChat(
            friendshipStatus.isEmpty() ||
            friendshipStatus.get() == Friendship.FriendshipStatus.ACCEPTED
        );

        return result;
    }
}
