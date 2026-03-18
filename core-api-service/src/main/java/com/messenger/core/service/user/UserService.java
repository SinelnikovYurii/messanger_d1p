package com.messenger.core.service.user;

import com.messenger.core.dto.UserDto;
import com.messenger.core.model.Friendship;
import com.messenger.core.model.User;
import com.messenger.core.service.encryption.EncryptionService;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Контракт для управления пользователями, их профилями и друзьями.
 * <p>
 * Криптографические операции (ключи, prekey bundle, backup) намеренно вынесены
 * в {@link EncryptionService} согласно принципу единственной ответственности (SRP).
 * Все зависимости в контроллерах и других сервисах должны инжектировать этот интерфейс,
 * а не конкретную реализацию {@link UserServiceImpl}.
 */
public interface UserService {

    List<UserDto.UserSearchResult> searchUsers(String query, Long currentUserId);

    UserDto getUserInfo(Long userId, Long currentUserId);

    void updateOnlineStatus(Long userId, boolean isOnline);

    UserDto updateProfile(Long userId, UserDto.UpdateProfileRequest request);

    String uploadAvatar(Long userId, MultipartFile file) throws IOException;

    Optional<User> findByUsername(String username);

    List<UserDto> getAllUsers(Long currentUserId);

    List<UserDto> getFriends(Long userId);

    Optional<Friendship.FriendshipStatus> getFriendshipStatus(Long currentUserId, Long targetUserId);

    UserDto convertToDto(User user);

    Optional<User> findUserById(Long id);

    void changePassword(Long userId, String newPassword);
}



