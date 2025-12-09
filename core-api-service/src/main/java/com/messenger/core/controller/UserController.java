package com.messenger.core.controller;

import com.messenger.core.dto.UserDto;
import com.messenger.core.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping("/all")
    // Явно указываем, что доступ к этому эндпоинту имеют пользователи с ролью ROLE_USER
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<List<UserDto>> getAllUsers(HttpServletRequest request) {
        log.info("Получен запрос на получение всех пользователей");

        // Получаем ID пользователя из заголовка запроса
        String userIdHeader = request.getHeader("x-user-id");
        log.info("ID пользователя из заголовка: {}", userIdHeader);

        if (userIdHeader == null) {
            log.error("Не указан ID пользователя в заголовке запроса");
            return ResponseEntity.badRequest().build();
        }

        try {
            Long currentUserId = Long.parseLong(userIdHeader);
            return ResponseEntity.ok(userService.getAllUsers(currentUserId));
        } catch (NumberFormatException e) {
            log.error("Некорректный формат ID пользователя: {}", userIdHeader);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/friends")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<List<UserDto>> getFriends(HttpServletRequest request) {
        log.info("Получен запрос на получение списка друзей");

        // Получаем ID пользователя из заголовка запроса
        String userIdHeader = request.getHeader("x-user-id");
        log.info("ID пользователя из заголовка: {}", userIdHeader);

        if (userIdHeader == null) {
            log.error("Не указан ID пользователя в заголовке запроса");
            return ResponseEntity.badRequest().build();
        }

        try {
            Long currentUserId = Long.parseLong(userIdHeader);
            return ResponseEntity.ok(userService.getFriends(currentUserId));
        } catch (NumberFormatException e) {
            log.error("Некорректный формат ID пользователя: {}", userIdHeader);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<List<UserDto.UserSearchResult>> searchUsers(
            @RequestParam String query,
            HttpServletRequest request) {
        log.info("Получен запрос на поиск пользователей с запросом: {}", query);

        // Получаем ID пользователя из заголовка запроса
        String userIdHeader = request.getHeader("x-user-id");
        log.info("ID пользователя из заголовка: {}", userIdHeader);

        if (userIdHeader == null) {
            log.error("Не указан ID пользователя в заголовке запроса");
            return ResponseEntity.badRequest().build();
        }

        try {
            Long currentUserId = Long.parseLong(userIdHeader);
            List<UserDto.UserSearchResult> results = userService.searchUsers(query, currentUserId);

            log.info("Найдено пользователей: {}", results.size());
            results.forEach(user ->
                log.info("User search result: id={}, username={}, profilePictureUrl={}",
                    user.getId(), user.getUsername(), user.getProfilePictureUrl())
            );

            return ResponseEntity.ok(results);
        } catch (NumberFormatException e) {
            log.error("Некорректный формат ID пользователя: {}", userIdHeader);
            return ResponseEntity.badRequest().build();
        }
    }

    // Обновить профиль пользователя
    @PutMapping("/profile")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> updateProfile(
            @RequestBody UserDto.UpdateProfileRequest request,
            HttpServletRequest httpRequest) {
        log.info("Получен запрос на обновление профиля: {}", request);

        String userIdHeader = httpRequest.getHeader("x-user-id");
        if (userIdHeader == null) {
            log.error("Не указан ID пользователя в заголовке запроса");
            return ResponseEntity.badRequest().body(Map.of("error", "Не указан ID пользователя"));
        }

        try {
            Long currentUserId = Long.parseLong(userIdHeader);
            log.info("Обновление профиля для пользователя с ID: {}", currentUserId);

            UserDto updatedUser = userService.updateProfile(currentUserId, request);
            return ResponseEntity.ok(updatedUser);
        } catch (NumberFormatException e) {
            log.error("Некорректный формат ID пользователя: {}", userIdHeader);
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректный формат ID"));
        } catch (RuntimeException e) {
            log.error("Ошибка при обновлении профиля: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Получить информацию о текущем пользователе
    @GetMapping("/profile")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> getCurrentUserProfile(HttpServletRequest httpRequest) {
        log.info("Получен запрос на получение информации о текущем пользователе");

        String userIdHeader = httpRequest.getHeader("x-user-id");
        if (userIdHeader == null) {
            log.error("Не указан ID пользователя в заголовке запроса");
            return ResponseEntity.badRequest().body(Map.of("error", "Не указан ID пользователя"));
        }

        try {
            Long currentUserId = Long.parseLong(userIdHeader);
            UserDto userInfo = userService.getUserInfo(currentUserId, currentUserId);
            return ResponseEntity.ok(userInfo);
        } catch (NumberFormatException e) {
            log.error("Некорректный формат ID пользователя: {}", userIdHeader);
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректный формат ID"));
        } catch (RuntimeException e) {
            log.error("Ошибка при получении информации о пользователе: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Загрузить аватар профиля
    @PostMapping("/profile/avatar")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            HttpServletRequest httpRequest) {
        log.info("Получен запрос на загрузку аватара профиля");

        String userIdHeader = httpRequest.getHeader("x-user-id");
        if (userIdHeader == null) {
            log.error("Не указан ID пользователя в заголовке запроса");
            return ResponseEntity.badRequest().body(Map.of("error", "Не указан ID пользователя"));
        }

        try {
            Long currentUserId = Long.parseLong(userIdHeader);

            // Проверяем, что это изображение
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Файл должен быть изображением"));
            }

            // УВЕЛИЧЕНО: Проверяем размер файла (максимум 10 МБ)
            if (file.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of("error", "Размер файла не должен превышать 10 МБ"));
            }

            log.info("Загрузка аватара для пользователя с ID: {}, размер: {} байт", currentUserId, file.getSize());

            String avatarUrl = userService.uploadAvatar(currentUserId, file);
            return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
        } catch (NumberFormatException e) {
            log.error("Некорректный формат ID пользователя: {}", userIdHeader);
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректный формат ID"));
        } catch (Exception e) {
            log.error("Ошибка при загрузке аватара: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Обновить онлайн-статус пользователя (вызывается WebSocket сервером)
    @PostMapping("/{userId}/status/online")
    public ResponseEntity<?> updateOnlineStatus(
            @PathVariable Long userId,
            @RequestParam boolean isOnline,
            HttpServletRequest httpRequest) {

        // Проверяем, что запрос от внутреннего сервиса
        String internalServiceHeader = httpRequest.getHeader("X-Internal-Service");
        if (!"websocket-server".equals(internalServiceHeader)) {
            log.warn("Попытка обновления онлайн-статуса не от WebSocket сервера");
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        log.info("Обновление онлайн-статуса для пользователя {}: {}", userId, isOnline);

        try {
            userService.updateOnlineStatus(userId, isOnline);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Ошибка при обновлении онлайн-статуса: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Получить данные пользователя по ID (для внутренних сервисов)
    @GetMapping("/{userId}/internal")
    public ResponseEntity<?> getUserDataInternal(
            @PathVariable Long userId,
            HttpServletRequest httpRequest) {

        // Проверяем, что запрос от внутреннего сервиса
        String internalServiceHeader = httpRequest.getHeader("X-Internal-Service");
        if (!"websocket-server".equals(internalServiceHeader)) {
            log.warn("Попытка получения данных пользователя не от внутреннего сервиса");
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        log.info("Получение данных пользователя {} для внутреннего сервиса", userId);

        try {
            UserDto user = userService.getUserInfo(userId, userId); // Используем userId для обоих параметров
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            log.error("Ошибка при получении данных пользователя: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Сохранить публичный ключ пользователя
    @PostMapping("/{userId}/public-key")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> savePublicKey(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        String publicKey = body.get("publicKey");
        if (publicKey == null || publicKey.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "publicKey is required"));
        }
        userService.savePublicKey(userId, publicKey);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // Получить публичный ключ пользователя
    @GetMapping("/{userId}/public-key")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> getPublicKey(@PathVariable Long userId) {
        String publicKey = userService.getPublicKey(userId);
        if (publicKey == null || publicKey.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("publicKey", publicKey));
    }

    // Сохранить X3DH prekey bundle
    @PostMapping("/{userId}/prekey-bundle")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> savePreKeyBundle(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        String identityKey = body.get("identityKey");
        String signedPreKey = body.get("signedPreKey");
        String oneTimePreKeys = body.get("oneTimePreKeys");
        String signedPreKeySignature = body.get("signedPreKeySignature");

        System.out.println("[PREKEY-SAVE] Received keys for user " + userId);
        System.out.println("[PREKEY-SAVE] identityKey=" + (identityKey != null ? identityKey.substring(0, Math.min(30, identityKey.length())) : "null"));
        System.out.println("[PREKEY-SAVE] signedPreKey=" + (signedPreKey != null ? signedPreKey.substring(0, Math.min(30, signedPreKey.length())) : "null"));

        // Проверяем только обязательные ключи (signedPreKeySignature опциональна)
        boolean needGen = identityKey == null || identityKey.isEmpty() ||
                         signedPreKey == null || signedPreKey.isEmpty() ||
                         oneTimePreKeys == null || oneTimePreKeys.isEmpty();

        if (needGen) {
            System.out.println("[PREKEY-SAVE] Missing required keys, auto-generating...");
            Map<String, String> generated = userService.generateAndSavePreKeyBundle(userId);
            return ResponseEntity.ok(Map.of("status", "generated", "bundle", generated));
        }

        System.out.println("[PREKEY-SAVE] Saving client-provided keys...");
        userService.savePreKeyBundle(userId, identityKey, signedPreKey, oneTimePreKeys, signedPreKeySignature);
        System.out.println("[PREKEY-SAVE] ✓ Keys saved successfully");

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // Получить X3DH prekey bundle
    @GetMapping("/{userId}/prekey-bundle")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> getPreKeyBundle(@PathVariable Long userId) {
        // Получаем prekey bundle из сервиса (должен быть объект с ключами)
        Map<String, String> bundle = userService.getPreKeyBundle(userId);
        if (bundle == null || bundle.isEmpty() ||
            bundle.get("identityKey") == null || bundle.get("identityKey").isEmpty() ||
            bundle.get("signedPreKey") == null || bundle.get("signedPreKey").isEmpty() ||
            bundle.get("oneTimePreKeys") == null || bundle.get("oneTimePreKeys").isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bundle);
    }

    // Получить PreKeyBundleProtocol для Double Ratchet
    @GetMapping("/{userId}/prekey-bundle-protocol")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> getPreKeyBundleProtocol(@PathVariable Long userId) {
        Map<String, Object> bundle = userService.getPreKeyBundleProtocol(userId);
        Object oneTimePreKeysObj = bundle.get("oneTimePreKeys");
        if (bundle == null || bundle.isEmpty() ||
            bundle.get("identityKey") == null || ((String)bundle.get("identityKey")).isEmpty() ||
            bundle.get("signedPreKey") == null || ((String)bundle.get("signedPreKey")).isEmpty() ||
            oneTimePreKeysObj == null || !(oneTimePreKeysObj instanceof List) || ((List<?>)oneTimePreKeysObj).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bundle);
    }
}
