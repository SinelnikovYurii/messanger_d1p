package com.messenger.core.service;

import com.messenger.core.dto.UserDto;
import com.messenger.core.model.User;
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
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<UserDto> getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public Optional<UserDto> getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public List<UserDto> getOnlineUsers() {
        return userRepository.findByIsOnlineTrue().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserDto> searchUsers(String query) {
        return userRepository.searchUsers(query).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserDto createUser(String username, String email, String displayName) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setIsOnline(false);

        User savedUser = userRepository.save(user);
        return convertToDto(savedUser);
    }

    @Transactional
    public void setUserOnlineStatus(Long userId, boolean isOnline) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setIsOnline(isOnline);
            if (!isOnline) {
                user.setLastSeenAt(LocalDateTime.now());
            }
            userRepository.save(user);
        });
    }

    @Transactional
    public UserDto updateUserProfile(Long userId, String displayName, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (displayName != null) {
            user.setDisplayName(displayName);
        }
        if (avatarUrl != null) {
            user.setAvatarUrl(avatarUrl);
        }

        User savedUser = userRepository.save(user);
        return convertToDto(savedUser);
    }

    private UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setDisplayName(user.getDisplayName());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setIsOnline(user.getIsOnline());
        dto.setLastSeenAt(user.getLastSeenAt());
        return dto;
    }
}
