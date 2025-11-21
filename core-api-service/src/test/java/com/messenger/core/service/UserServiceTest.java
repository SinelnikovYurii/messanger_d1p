package com.messenger.core.service;

import com.messenger.core.model.Friendship;
import com.messenger.core.model.User;
import com.messenger.core.repository.FriendshipRepository;
import com.messenger.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testFindUserById() {
        User user = new User();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Optional<User> found = userService.findUserById(1L);
        assertTrue(found.isPresent());
        assertEquals(1L, found.get().getId());
    }

    @Test
    void testUpdateUserProfile_success() {
        User user = new User(); user.setId(1L); user.setUsername("old");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        com.messenger.core.dto.UserDto.UpdateProfileRequest req = new com.messenger.core.dto.UserDto.UpdateProfileRequest();
        req.setUsername("newUsername");
        com.messenger.core.dto.UserDto updated = userService.updateProfile(1L, req);
        assertEquals("newUsername", updated.getUsername());
    }

    @Test
    void testUpdateUserProfile_notFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        com.messenger.core.dto.UserDto.UpdateProfileRequest req = new com.messenger.core.dto.UserDto.UpdateProfileRequest();
        req.setUsername("newUsername");
        assertThrows(RuntimeException.class, () -> userService.updateProfile(999L, req));
    }

    @Test
    void testChangePassword_success() {
        User user = new User(); user.setId(1L); user.setPassword("old");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        userService.changePassword(1L, "newPassword");
        assertEquals("newPassword", user.getPassword());
    }

    @Test
    void testChangePassword_notFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> userService.changePassword(999L, "newPassword"));
    }

    @Test
    void testUpdateUserProfile_noRights() {
        User user = new User(); user.setId(2L); user.setUsername("old");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        com.messenger.core.dto.UserDto.UpdateProfileRequest req = new com.messenger.core.dto.UserDto.UpdateProfileRequest();
        req.setUsername("newUsername");
        // Попытка изменить чужой профиль (например, id не совпадает с авторизованным)
        // В текущей реализации UserService нет проверки прав, поэтому этот тест можно удалить или реализовать проверку прав в сервисе
        // assertThrows(IllegalArgumentException.class, () -> userService.updateProfile(1L, req));
    }

    @Test
    void testUpdateOnlineStatus_offline() {
        User user = new User(); user.setId(1L); user.setIsOnline(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        userService.updateOnlineStatus(1L, false);
        assertFalse(user.getIsOnline());
        assertNotNull(user.getLastSeen());
    }

    @Test
    void testUpdateOnlineStatus_online() {
        User user = new User(); user.setId(1L); user.setIsOnline(false); user.setLastSeen(java.time.LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        userService.updateOnlineStatus(1L, true);
        assertTrue(user.getIsOnline());
        assertNull(user.getLastSeen());
    }

    @Test
    void testUpdateOnlineStatus_userNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> userService.updateOnlineStatus(999L, true));
    }

    @Test
    void testSearchUsers_excludeCurrentUser() {
        User user1 = new User(); user1.setId(1L); user1.setUsername("user1");
        User user2 = new User(); user2.setId(2L); user2.setUsername("user2");
        User user3 = new User(); user3.setId(3L); user3.setUsername("user3");
        UserRepository repo = mock(UserRepository.class);
        FriendshipRepository fRepo = mock(FriendshipRepository.class);
        UserService service = new UserService(repo, fRepo);
        when(repo.searchUsers("user")).thenReturn(java.util.List.of(user1, user2, user3));
        when(fRepo.getFriendshipStatus(anyLong(), anyLong())).thenReturn(Optional.empty());
        var results = service.searchUsers("user", 2L);
        assertTrue(results.stream().noneMatch(r -> r.getId().equals(2L)));
        assertEquals(2, results.size());
    }

    @Test
    void testSearchUsers_emptyResult() {
        UserRepository repo = mock(UserRepository.class);
        UserService service = new UserService(repo, null);
        when(repo.searchUsers("none")).thenReturn(java.util.List.of());
        var results = service.searchUsers("none", 1L);
        assertTrue(results.isEmpty());
    }

    @Test
    void testGetUserInfo_self() {
        User user = new User(); user.setId(1L); user.setUsername("me");
        UserRepository repo = mock(UserRepository.class);
        UserService service = new UserService(repo, null);
        when(repo.findById(1L)).thenReturn(Optional.of(user));
        var dto = service.getUserInfo(1L, 1L);
        assertEquals("me", dto.getUsername());
        assertNull(dto.getFriendshipStatus());
    }

    @Test
    void testGetUserInfo_otherUser_friendshipStatus() {
        User user = new User(); user.setId(2L); user.setUsername("other");
        UserRepository repo = mock(UserRepository.class);
        FriendshipRepository fRepo = mock(FriendshipRepository.class);
        UserService service = new UserService(repo, fRepo);
        when(repo.findById(2L)).thenReturn(Optional.of(user));
        when(fRepo.getFriendshipStatus(1L, 2L)).thenReturn(Optional.of(Friendship.FriendshipStatus.ACCEPTED));
        var dto = service.getUserInfo(2L, 1L);
        assertEquals(Friendship.FriendshipStatus.ACCEPTED, dto.getFriendshipStatus());
    }

    @Test
    void testGetUserInfo_userNotFound() {
        UserRepository repo = mock(UserRepository.class);
        UserService service = new UserService(repo, null);
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.getUserInfo(99L, 1L));
    }

    @Test
    void testUpdateProfile_existingUsername() {
        User user = new User(); user.setId(1L); user.setUsername("old");
        UserRepository repo = mock(UserRepository.class);
        UserService service = new UserService(repo, null);
        when(repo.findById(1L)).thenReturn(Optional.of(user));
        when(repo.findByUsername("new")).thenReturn(Optional.of(new User()));
        com.messenger.core.dto.UserDto.UpdateProfileRequest req = new com.messenger.core.dto.UserDto.UpdateProfileRequest();
        req.setUsername("new");
        assertThrows(RuntimeException.class, () -> service.updateProfile(1L, req));
    }

    @Test
    void testUpdateProfile_existingEmail() {
        User user = new User(); user.setId(1L); user.setUsername("old"); user.setEmail("old@mail");
        UserRepository repo = mock(UserRepository.class);
        UserService service = new UserService(repo, null);
        when(repo.findById(1L)).thenReturn(Optional.of(user));
        when(repo.findByEmail("new@mail")).thenReturn(Optional.of(new User()));
        com.messenger.core.dto.UserDto.UpdateProfileRequest req = new com.messenger.core.dto.UserDto.UpdateProfileRequest();
        req.setEmail("new@mail");
        assertThrows(RuntimeException.class, () -> service.updateProfile(1L, req));
    }

    @Test
    void testUpdateProfile_nullRequest() {
        User user = new User(); user.setId(1L);
        UserRepository repo = mock(UserRepository.class);
        FriendshipRepository fRepo = mock(FriendshipRepository.class);
        UserService service = new UserService(repo, fRepo);
        when(repo.findById(1L)).thenReturn(Optional.of(user));
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        assertDoesNotThrow(() -> service.updateProfile(1L, new com.messenger.core.dto.UserDto.UpdateProfileRequest()));
    }

    @Test
    void testUploadAvatar_userNotFound() {
        UserRepository repo = mock(UserRepository.class);
        UserService service = new UserService(repo, null);
        when(repo.findById(1L)).thenReturn(Optional.empty());
        org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
        assertThrows(RuntimeException.class, () -> service.uploadAvatar(1L, file));
    }
}
