package com.messenger.core.controller;

import com.messenger.core.dto.UserDto;
import com.messenger.core.security.JwtService;
import com.messenger.core.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;

    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        List<UserDto> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<UserDto> getUserByUsername(@PathVariable String username) {
        return userService.getUserByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/online")
    public ResponseEntity<List<UserDto>> getOnlineUsers() {
        List<UserDto> onlineUsers = userService.getOnlineUsers();
        return ResponseEntity.ok(onlineUsers);
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserDto>> searchUsers(@RequestParam String query) {
        List<UserDto> users = userService.searchUsers(query);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(HttpServletRequest request) {
        Long userId = extractUserIdFromToken(request);
        return userService.getUserById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/me")
    public ResponseEntity<UserDto> updateProfile(
            @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromToken(httpRequest);
        UserDto updatedUser = userService.updateUserProfile(
                userId, request.getDisplayName(), request.getAvatarUrl());
        return ResponseEntity.ok(updatedUser);
    }

    @PutMapping("/status")
    public ResponseEntity<Void> updateOnlineStatus(
            @RequestBody OnlineStatusRequest request,
            HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromToken(httpRequest);
        userService.setUserOnlineStatus(userId, request.isOnline());
        return ResponseEntity.ok().build();
    }

    private Long extractUserIdFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtService.extractUserId(token);
        }
        throw new RuntimeException("No valid token found");
    }

    // Inner classes for request bodies
    public static class UpdateProfileRequest {
        private String displayName;
        private String avatarUrl;

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    }

    public static class OnlineStatusRequest {
        private boolean isOnline;

        public boolean isOnline() { return isOnline; }
        public void setOnline(boolean online) { isOnline = online; }
    }
}
