package com.messenger.core.controller;

import com.messenger.core.dto.UserDto;
import com.messenger.core.service.encryption.EncryptionService;
import com.messenger.core.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Контроллер для управления пользователями и их профилями.
 * <p>
 * Делегирует:
 * <ul>
 *   <li>Операции с профилями и друзьями — в {@link UserService}.</li>
 *   <li>Криптографические операции (ключи, prekey bundle, backup) — в {@link EncryptionService}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    /** Сервис управления пользователями (профили, друзья, статусы). */
    private final UserService userService;

    /** Сервис управления E2EE-ключами пользователей. */
    private final EncryptionService encryptionService;


    /**
     * Получить всех пользователей, кроме текущего.
     *
     * @param request HTTP-запрос для извлечения заголовка {@code x-user-id}
     * @return список DTO пользователей
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<List<UserDto>> getAllUsers(HttpServletRequest request) {
        String userIdHeader = request.getHeader("x-user-id");
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

    /**
     * Получить список друзей текущего пользователя.
     *
     * @param request HTTP-запрос для извлечения заголовка {@code x-user-id}
     * @return список DTO друзей
     */
    @GetMapping("/friends")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<List<UserDto>> getFriends(HttpServletRequest request) {
        String userIdHeader = request.getHeader("x-user-id");
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

    /**
     * Поиск пользователей по строке запроса.
     * Текущий пользователь исключается из результатов.
     *
     * @param query   поисковая строка
     * @param request HTTP-запрос для извлечения заголовка {@code x-user-id}
     * @return список результатов поиска
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<List<UserDto.UserSearchResult>> searchUsers(
            @RequestParam String query,
            HttpServletRequest request) {
        String userIdHeader = request.getHeader("x-user-id");
        if (userIdHeader == null) {
            log.error("Не указан ID пользователя в заголовке запроса");
            return ResponseEntity.badRequest().build();
        }
        try {
            Long currentUserId = Long.parseLong(userIdHeader);
            List<UserDto.UserSearchResult> results = userService.searchUsers(query, currentUserId);
            log.info("Поиск '{}': найдено {} пользователей", query, results.size());
            return ResponseEntity.ok(results);
        } catch (NumberFormatException e) {
            log.error("Некорректный формат ID пользователя: {}", userIdHeader);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Обновить данные профиля текущего пользователя.
     *
     * @param request     объект с новыми данными профиля
     * @param httpRequest HTTP-запрос для извлечения заголовка {@code x-user-id}
     * @return обновлённый DTO пользователя или описание ошибки
     */
    @PutMapping("/profile")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> updateProfile(
            @RequestBody UserDto.UpdateProfileRequest request,
            HttpServletRequest httpRequest) {
        String userIdHeader = httpRequest.getHeader("x-user-id");
        if (userIdHeader == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Не указан ID пользователя"));
        }
        try {
            Long currentUserId = Long.parseLong(userIdHeader);
            log.info("Обновление профиля пользователя {}", currentUserId);
            return ResponseEntity.ok(userService.updateProfile(currentUserId, request));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректный формат ID"));
        } catch (RuntimeException e) {
            log.error("Ошибка обновления профиля: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Получить информацию о текущем пользователе.
     *
     * @param httpRequest HTTP-запрос для извлечения заголовка {@code x-user-id}
     * @return DTO текущего пользователя или описание ошибки
     */
    @GetMapping("/profile")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> getCurrentUserProfile(HttpServletRequest httpRequest) {
        String userIdHeader = httpRequest.getHeader("x-user-id");
        if (userIdHeader == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Не указан ID пользователя"));
        }
        try {
            Long currentUserId = Long.parseLong(userIdHeader);
            return ResponseEntity.ok(userService.getUserInfo(currentUserId, currentUserId));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректный формат ID"));
        } catch (RuntimeException e) {
            log.error("Ошибка получения профиля: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Загрузить аватар текущего пользователя.
     * Принимает файл изображения размером до 10 МБ.
     *
     * @param file        загружаемый файл изображения
     * @param httpRequest HTTP-запрос для извлечения заголовка {@code x-user-id}
     * @return URL сохранённого аватара или описание ошибки
     */
    @PostMapping("/profile/avatar")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            HttpServletRequest httpRequest) {
        String userIdHeader = httpRequest.getHeader("x-user-id");
        if (userIdHeader == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Не указан ID пользователя"));
        }
        try {
            Long currentUserId = Long.parseLong(userIdHeader);
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Файл должен быть изображением"));
            }
            if (file.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of("error", "Размер файла не должен превышать 10 МБ"));
            }
            log.info("Загрузка аватара: userId={}, size={} байт", currentUserId, file.getSize());
            String avatarUrl = userService.uploadAvatar(currentUserId, file);
            return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректный формат ID"));
        } catch (Exception e) {
            log.error("Ошибка загрузки аватара: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Обновить онлайн-статус пользователя.
     * Вызывается только WebSocket-сервером (проверяется заголовок {@code X-Internal-Service}).
     *
     * @param userId      ID пользователя
     * @param isOnline    новый статус присутствия
     * @param httpRequest HTTP-запрос для проверки заголовка внутреннего сервиса
     * @return результат обновления или 403
     */
    @PostMapping("/{userId}/status/online")
    public ResponseEntity<?> updateOnlineStatus(
            @PathVariable Long userId,
            @RequestParam boolean isOnline,
            HttpServletRequest httpRequest) {
        if (!"websocket-server".equals(httpRequest.getHeader("X-Internal-Service"))) {
            log.warn("Запрос на изменение статуса не от WebSocket-сервера");
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        try {
            userService.updateOnlineStatus(userId, isOnline);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Ошибка обновления онлайн-статуса: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Получить данные пользователя по ID для внутренних сервисов.
     * Проверяется заголовок {@code X-Internal-Service}.
     *
     * @param userId      ID пользователя
     * @param httpRequest HTTP-запрос для проверки заголовка внутреннего сервиса
     * @return DTO пользователя или 403
     */
    @GetMapping("/{userId}/internal")
    public ResponseEntity<?> getUserDataInternal(
            @PathVariable Long userId,
            HttpServletRequest httpRequest) {
        if (!"websocket-server".equals(httpRequest.getHeader("X-Internal-Service"))) {
            log.warn("Запрос данных пользователя не от внутреннего сервиса");
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        try {
            return ResponseEntity.ok(userService.getUserInfo(userId, userId));
        } catch (Exception e) {
            log.error("Ошибка получения данных пользователя {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Криптографические операции (делегируются в EncryptionService)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Сохранить публичный ключ пользователя.
     *
     * @param userId ID пользователя
     * @param body   тело запроса: {@code { "publicKey": "..." }}
     * @return статус сохранения
     */
    @PostMapping("/{userId}/public-key")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> savePublicKey(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        String publicKey = body.get("publicKey");
        if (publicKey == null || publicKey.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "publicKey обязателен"));
        }
        encryptionService.savePublicKey(userId, publicKey);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * Получить публичный ключ пользователя.
     *
     * @param userId ID пользователя
     * @return {@code { "publicKey": "..." }} или 404, если ключ не найден
     */
    @GetMapping("/{userId}/public-key")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> getPublicKey(@PathVariable Long userId) {
        String publicKey = encryptionService.getPublicKey(userId);
        if (publicKey == null || publicKey.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("publicKey", publicKey));
    }

    /**
     * Сохранить X3DH prekey bundle пользователя.
     * Если обязательные ключи отсутствуют — автоматически генерирует новый bundle.
     *
     * @param userId ID пользователя
     * @param body   тело запроса с полями identityKey, signedPreKey, oneTimePreKeys, signedPreKeySignature
     * @return статус {@code ok} или {@code generated} с новым bundle
     */
    @PostMapping("/{userId}/prekey-bundle")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> savePreKeyBundle(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        String identityKey          = body.get("identityKey");
        String signedPreKey         = body.get("signedPreKey");
        String oneTimePreKeys       = body.get("oneTimePreKeys");
        String signedPreKeySignature = body.get("signedPreKeySignature");

        boolean needGen = identityKey == null || identityKey.isEmpty()
                || signedPreKey == null || signedPreKey.isEmpty()
                || oneTimePreKeys == null || oneTimePreKeys.isEmpty();

        if (needGen) {
            log.info("Ключи не переданы для пользователя {}, выполняется автогенерация", userId);
            Map<String, String> generated = encryptionService.generateAndSavePreKeyBundle(userId);
            return ResponseEntity.ok(Map.of("status", "generated", "bundle", generated));
        }

        encryptionService.savePreKeyBundle(userId, identityKey, signedPreKey, oneTimePreKeys, signedPreKeySignature);
        log.info("Prekey bundle сохранён для пользователя {}", userId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * Получить X3DH prekey bundle пользователя.
     * Если ключи отсутствуют — возвращается автоматически сгенерированный bundle.
     *
     * @param userId ID пользователя
     * @return prekey bundle или 404, если данные неполные
     */
    @GetMapping("/{userId}/prekey-bundle")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> getPreKeyBundle(@PathVariable Long userId) {
        Map<String, String> bundle = encryptionService.getPreKeyBundle(userId);
        if (bundle == null || bundle.isEmpty()
                || bundle.get("identityKey") == null || bundle.get("identityKey").isEmpty()
                || bundle.get("signedPreKey") == null || bundle.get("signedPreKey").isEmpty()
                || bundle.get("oneTimePreKeys") == null || bundle.get("oneTimePreKeys").isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bundle);
    }

    /**
     * Получить PreKeyBundleProtocol для протокола Double Ratchet.
     *
     * @param userId ID пользователя
     * @return расширенный bundle с разобранным списком oneTimePreKeys или 404
     */
    @GetMapping("/{userId}/prekey-bundle-protocol")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> getPreKeyBundleProtocol(@PathVariable Long userId) {
        Map<String, Object> bundle = encryptionService.getPreKeyBundleProtocol(userId);
        Object oneTimePreKeysObj = bundle.get("oneTimePreKeys");
        if (bundle.isEmpty()
                || bundle.get("identityKey") == null || ((String) bundle.get("identityKey")).isEmpty()
                || bundle.get("signedPreKey") == null || ((String) bundle.get("signedPreKey")).isEmpty()
                || !(oneTimePreKeysObj instanceof List) || ((List<?>) oneTimePreKeysObj).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bundle);
    }

    /**
     * Сохранить зашифрованный бекап E2EE-ключей текущего пользователя.
     * Сервер хранит только зашифрованный blob — пароль шифрования серверу неизвестен.
     *
     * @param body        тело запроса: {@code { "encryptedPayload": "..." }}
     * @param httpRequest HTTP-запрос для извлечения заголовка {@code x-user-id}
     * @return статус сохранения
     */
    @PostMapping("/me/key-backup")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> saveKeyBackup(
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        String userIdHeader = httpRequest.getHeader("x-user-id");
        if (userIdHeader == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Не указан ID пользователя"));
        }
        String encryptedPayload = body.get("encryptedPayload");
        if (encryptedPayload == null || encryptedPayload.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "encryptedPayload обязателен"));
        }
        try {
            encryptionService.saveEncryptedKeyBackup(Long.parseLong(userIdHeader), encryptedPayload);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректный формат ID"));
        } catch (Exception e) {
            log.error("Ошибка сохранения бекапа ключей: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Получить зашифрованный бекап E2EE-ключей текущего пользователя.
     *
     * @param httpRequest HTTP-запрос для извлечения заголовка {@code x-user-id}
     * @return {@code { "encryptedPayload": "..." }} или 404, если бекап не найден
     */
    @GetMapping("/me/key-backup")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<?> getKeyBackup(HttpServletRequest httpRequest) {
        String userIdHeader = httpRequest.getHeader("x-user-id");
        if (userIdHeader == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Не указан ID пользователя"));
        }
        try {
            String encryptedPayload = encryptionService.getEncryptedKeyBackup(Long.parseLong(userIdHeader));
            if (encryptedPayload == null || encryptedPayload.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(Map.of("encryptedPayload", encryptedPayload));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректный формат ID"));
        } catch (Exception e) {
            log.error("Ошибка получения бекапа ключей: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
