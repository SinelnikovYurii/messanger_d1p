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
import java.security.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Base64;
import java.util.stream.Collectors;
import java.security.spec.ECGenParameterSpec;

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

    /**
     * Найти пользователя по ID
     */
    public Optional<User> findUserById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Сменить пароль пользователя
     */
    public void changePassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        user.setPassword(newPassword); // В реальном проекте пароль должен быть захеширован!
        userRepository.save(user);
    }

    /**
     * Сохранить публичный ключ пользователя
     */
    public void savePublicKey(Long userId, String publicKey) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        user.setPublicKey(publicKey);
        userRepository.save(user);
    }

    /**
     * Получить публичный ключ пользователя
     */
    @Transactional(readOnly = true)
    public String getPublicKey(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        return user.getPublicKey();
    }

    /**
     * Сохранить X3DH prekey bundle пользователя
     */
    public void savePreKeyBundle(Long userId, String identityKey, String signedPreKey, String oneTimePreKeys, String signedPreKeySignature) {
        if (identityKey == null || identityKey.isEmpty() ||
            signedPreKey == null || signedPreKey.isEmpty() ||
            oneTimePreKeys == null || oneTimePreKeys.isEmpty()) {
            throw new IllegalArgumentException("identityKey, signedPreKey и oneTimePreKeys обязательны");
        }

        System.out.println("[UserService] Saving prekey bundle for user " + userId);
        System.out.println("[UserService] identityKey: " + identityKey.substring(0, Math.min(30, identityKey.length())) + "...");

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        user.setIdentityKey(identityKey);
        user.setSignedPreKey(signedPreKey);
        user.setOneTimePreKeys(oneTimePreKeys);
        user.setSignedPreKeySignature(signedPreKeySignature != null ? signedPreKeySignature : "");
        user.setPublicKey(identityKey);
        userRepository.save(user);

        System.out.println("[UserService] ✓ Saved successfully for user " + userId);
    }

    /**
     * Получить X3DH prekey bundle пользователя
     */
    @Transactional
    public Map<String, String> getPreKeyBundle(Long userId) {
        System.out.println("[UserService-GET] Getting prekey bundle for user " + userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        System.out.println("[UserService-GET] User found: " + user.getUsername());
        System.out.println("[UserService-GET] identityKey: " + (user.getIdentityKey() != null ? user.getIdentityKey().substring(0, Math.min(30, user.getIdentityKey().length())) + "..." : "NULL"));
        System.out.println("[UserService-GET] signedPreKey: " + (user.getSignedPreKey() != null ? user.getSignedPreKey().substring(0, Math.min(30, user.getSignedPreKey().length())) + "..." : "NULL"));

        boolean needGen = user.getIdentityKey() == null || user.getIdentityKey().isEmpty() ||
                          user.getSignedPreKey() == null || user.getSignedPreKey().isEmpty() ||
                          user.getOneTimePreKeys() == null || user.getOneTimePreKeys().isEmpty();

        if (needGen) {
            System.out.println("[UserService-GET] Keys are missing, AUTO-GENERATING new keys...");
            try {
                // identityKey (ECDH) - используем кривую P-256 (secp256r1) для совместимости с Web Crypto API
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
                ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
                kpg.initialize(ecSpec);
                KeyPair identityKeyPair = kpg.generateKeyPair();
                java.security.interfaces.ECPublicKey ecPub = (java.security.interfaces.ECPublicKey) identityKeyPair.getPublic();
                java.security.spec.ECPoint w = ecPub.getW();
                byte[] x = w.getAffineX().toByteArray();
                byte[] y = w.getAffineY().toByteArray();
                if (x.length > 32) x = java.util.Arrays.copyOfRange(x, x.length - 32, x.length);
                if (x.length < 32) {
                    byte[] tmp = new byte[32];
                    System.arraycopy(x, 0, tmp, 32 - x.length, x.length);
                    x = tmp;
                }
                if (y.length > 32) y = java.util.Arrays.copyOfRange(y, y.length - 32, y.length);
                if (y.length < 32) {
                    byte[] tmp = new byte[32];
                    System.arraycopy(y, 0, tmp, 32 - y.length, y.length);
                    y = tmp;
                }
                byte[] uncompressed = new byte[65];
                uncompressed[0] = 0x04;
                System.arraycopy(x, 0, uncompressed, 1, 32);
                System.arraycopy(y, 0, uncompressed, 33, 32);
                String identityKeyPubRaw = Base64.getEncoder().encodeToString(uncompressed);
                // signedPreKey
                KeyPair signedPreKeyPair = kpg.generateKeyPair();
                java.security.interfaces.ECPublicKey ecSignedPub = (java.security.interfaces.ECPublicKey) signedPreKeyPair.getPublic();
                java.security.spec.ECPoint w2 = ecSignedPub.getW();
                byte[] x2 = w2.getAffineX().toByteArray();
                byte[] y2 = w2.getAffineY().toByteArray();
                if (x2.length > 32) x2 = java.util.Arrays.copyOfRange(x2, x2.length - 32, x2.length);
                if (x2.length < 32) {
                    byte[] tmp = new byte[32];
                    System.arraycopy(x2, 0, tmp, 32 - x2.length, x2.length);
                    x2 = tmp;
                }
                if (y2.length > 32) y2 = java.util.Arrays.copyOfRange(y2, y2.length - 32, y2.length);
                if (y2.length < 32) {
                    byte[] tmp = new byte[32];
                    System.arraycopy(y2, 0, tmp, 32 - y2.length, y2.length);
                    y2 = tmp;
                }
                byte[] uncompressed2 = new byte[65];
                uncompressed2[0] = 0x04;
                System.arraycopy(x2, 0, uncompressed2, 1, 32);
                System.arraycopy(y2, 0, uncompressed2, 33, 32);
                String signedPreKeyPubRaw = Base64.getEncoder().encodeToString(uncompressed2);
                // oneTimePreKeys
                StringBuilder oneTimePreKeysJson = new StringBuilder("[");
                for (int i = 0; i < 5; i++) {
                    KeyPair oneTimePreKeyPair = kpg.generateKeyPair();
                    java.security.interfaces.ECPublicKey ecOneTimePub = (java.security.interfaces.ECPublicKey) oneTimePreKeyPair.getPublic();
                    java.security.spec.ECPoint w3 = ecOneTimePub.getW();
                    byte[] x3 = w3.getAffineX().toByteArray();
                    byte[] y3 = w3.getAffineY().toByteArray();
                    if (x3.length > 32) x3 = java.util.Arrays.copyOfRange(x3, x3.length - 32, x3.length);
                    if (x3.length < 32) {
                        byte[] tmp = new byte[32];
                        System.arraycopy(x3, 0, tmp, 32 - x3.length, x3.length);
                        x3 = tmp;
                    }
                    if (y3.length > 32) y3 = java.util.Arrays.copyOfRange(y3, y3.length - 32, y3.length);
                    if (y3.length < 32) {
                        byte[] tmp = new byte[32];
                        System.arraycopy(y3, 0, tmp, 32 - y3.length, y3.length);
                        y3 = tmp;
                    }
                    byte[] uncompressed3 = new byte[65];
                    uncompressed3[0] = 0x04;
                    System.arraycopy(x3, 0, uncompressed3, 1, 32);
                    System.arraycopy(y3, 0, uncompressed3, 33, 32);
                    String oneTimePreKeyPubRaw = Base64.getEncoder().encodeToString(uncompressed3);
                    oneTimePreKeysJson.append('"').append(oneTimePreKeyPubRaw).append('"');
                    if (i < 4) oneTimePreKeysJson.append(',');
                }
                oneTimePreKeysJson.append("]");
                // Генерация подписи signedPreKey приватным ключом identityKey
                Signature ecdsaSign = Signature.getInstance("SHA256withECDSA");
                ecdsaSign.initSign(identityKeyPair.getPrivate());
                ecdsaSign.update(signedPreKeyPair.getPublic().getEncoded());
                byte[] signatureBytes = ecdsaSign.sign();
                String signedPreKeySignature = Base64.getEncoder().encodeToString(signatureBytes);
                // Сохраняем ключи и подпись
                user.setIdentityKey(identityKeyPubRaw);
                user.setSignedPreKey(signedPreKeyPubRaw);
                user.setOneTimePreKeys(oneTimePreKeysJson.toString());
                user.setSignedPreKeySignature(signedPreKeySignature);
                user.setPublicKey(identityKeyPubRaw);
                userRepository.save(user);
                System.out.println("[UserService-GET] ✓ Auto-generated and saved new keys");
            } catch (Exception e) {
                throw new RuntimeException("Ошибка генерации ключей X3DH", e);
            }
        } else {
            System.out.println("[UserService-GET] ✓ Returning EXISTING keys from DB");
        }

        Map<String, String> bundle = new HashMap<>();
        bundle.put("identityKey", user.getIdentityKey());
        bundle.put("signedPreKey", user.getSignedPreKey());
        bundle.put("oneTimePreKeys", user.getOneTimePreKeys());
        bundle.put("publicKey", user.getPublicKey());

        System.out.println("[UserService-GET] Returning bundle for user " + userId + ": identityKey=" +
            (user.getIdentityKey() != null ? user.getIdentityKey().substring(0, Math.min(30, user.getIdentityKey().length())) + "..." : "NULL"));

        return bundle;
    }

    /**
     * Получить X3DH prekey bundle пользователя в бинарном виде (JSON -> byte[])
     */
    @Transactional
    public byte[] getPreKeyBundleBinary(Long userId) {
        Map<String, String> bundle = getPreKeyBundle(userId);
        if (bundle == null || bundle.isEmpty()) return new byte[0];
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(bundle);
            return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Логирование ошибки
            return new byte[0];
        }
    }

    /**
     * Получить PreKeyBundleProtocol для Double Ratchet
     */
    @Transactional
    public Map<String, Object> getPreKeyBundleProtocol(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        Map<String, Object> bundle = new HashMap<>();
        bundle.put("userId", user.getId());
        bundle.put("identityKey", user.getIdentityKey());
        bundle.put("signedPreKey", user.getSignedPreKey());
        bundle.put("signedPreKeySignature", user.getSignedPreKeySignature());
        // Преобразуем oneTimePreKeys из строки JSON в массив
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<String> oneTimePreKeysList = mapper.readValue(user.getOneTimePreKeys(), List.class);
            bundle.put("oneTimePreKeys", oneTimePreKeysList);
        } catch (Exception e) {
            bundle.put("oneTimePreKeys", new String[]{});
        }
        bundle.put("publicKey", user.getPublicKey());

        return bundle;
    }

    /**
     * Генерировать и сохранить X3DH prekey bundle пользователя
     */
    public Map<String, String> generateAndSavePreKeyBundle(Long userId) {
        // Принудительно генерируем новые ключи, не используем getPreKeyBundle
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
            kpg.initialize(ecSpec);
            KeyPair identityKeyPair = kpg.generateKeyPair();
            java.security.interfaces.ECPublicKey ecPub = (java.security.interfaces.ECPublicKey) identityKeyPair.getPublic();
            java.security.spec.ECPoint w = ecPub.getW();
            byte[] x = w.getAffineX().toByteArray();
            byte[] y = w.getAffineY().toByteArray();
            if (x.length > 32) x = java.util.Arrays.copyOfRange(x, x.length - 32, x.length);
            if (x.length < 32) {
                byte[] tmp = new byte[32];
                System.arraycopy(x, 0, tmp, 32 - x.length, x.length);
                x = tmp;
            }
            if (y.length > 32) y = java.util.Arrays.copyOfRange(y, y.length - 32, y.length);
            if (y.length < 32) {
                byte[] tmp = new byte[32];
                System.arraycopy(y, 0, tmp, 32 - y.length, y.length);
                y = tmp;
            }
            byte[] uncompressed = new byte[65];
            uncompressed[0] = 0x04;
            System.arraycopy(x, 0, uncompressed, 1, 32);
            System.arraycopy(y, 0, uncompressed, 33, 32);
            String identityKeyPubRaw = Base64.getEncoder().encodeToString(uncompressed);
            // signedPreKey
            KeyPair signedPreKeyPair = kpg.generateKeyPair();
            java.security.interfaces.ECPublicKey ecSignedPub = (java.security.interfaces.ECPublicKey) signedPreKeyPair.getPublic();
            java.security.spec.ECPoint w2 = ecSignedPub.getW();
            byte[] x2 = w2.getAffineX().toByteArray();
            byte[] y2 = w2.getAffineY().toByteArray();
            if (x2.length > 32) x2 = java.util.Arrays.copyOfRange(x2, x2.length - 32, x2.length);
            if (x2.length < 32) {
                byte[] tmp = new byte[32];
                System.arraycopy(x2, 0, tmp, 32 - x2.length, x2.length);
                x2 = tmp;
            }
            if (y2.length > 32) y2 = java.util.Arrays.copyOfRange(y2, y2.length - 32, y2.length);
            if (y2.length < 32) {
                byte[] tmp = new byte[32];
                System.arraycopy(y2, 0, tmp, 32 - y2.length, y2.length);
                y2 = tmp;
            }
            byte[] uncompressed2 = new byte[65];
            uncompressed2[0] = 0x04;
            System.arraycopy(x2, 0, uncompressed2, 1, 32);
            System.arraycopy(y2, 0, uncompressed2, 33, 32);
            String signedPreKeyPubRaw = Base64.getEncoder().encodeToString(uncompressed2);
            // oneTimePreKeys
            StringBuilder oneTimePreKeysJson = new StringBuilder("[");
            for (int i = 0; i < 5; i++) {
                KeyPair oneTimePreKeyPair = kpg.generateKeyPair();
                java.security.interfaces.ECPublicKey ecOneTimePub = (java.security.interfaces.ECPublicKey) oneTimePreKeyPair.getPublic();
                java.security.spec.ECPoint w3 = ecOneTimePub.getW();
                byte[] x3 = w3.getAffineX().toByteArray();
                byte[] y3 = w3.getAffineY().toByteArray();
                if (x3.length > 32) x3 = java.util.Arrays.copyOfRange(x3, x3.length - 32, x3.length);
                if (x3.length < 32) {
                    byte[] tmp = new byte[32];
                    System.arraycopy(x3, 0, tmp, 32 - x3.length, x3.length);
                    x3 = tmp;
                }
                if (y3.length > 32) y3 = java.util.Arrays.copyOfRange(y3, y3.length - 32, y3.length);
                if (y3.length < 32) {
                    byte[] tmp = new byte[32];
                    System.arraycopy(y3, 0, tmp, 32 - y3.length, y3.length);
                    y3 = tmp;
                }
                byte[] uncompressed3 = new byte[65];
                uncompressed3[0] = 0x04;
                System.arraycopy(x3, 0, uncompressed3, 1, 32);
                System.arraycopy(y3, 0, uncompressed3, 33, 32);
                String oneTimePreKeyPubRaw = Base64.getEncoder().encodeToString(uncompressed3);
                oneTimePreKeysJson.append('"').append(oneTimePreKeyPubRaw).append('"');
                if (i < 4) oneTimePreKeysJson.append(',');
            }
            oneTimePreKeysJson.append("]");
            // Генерация подписи signedPreKey приватным ключом identityKey
            Signature ecdsaSign = Signature.getInstance("SHA256withECDSA");
            ecdsaSign.initSign(identityKeyPair.getPrivate());
            ecdsaSign.update(signedPreKeyPair.getPublic().getEncoded());
            byte[] signatureBytes = ecdsaSign.sign();
            String signedPreKeySignature = Base64.getEncoder().encodeToString(signatureBytes);
            // Сохраняем ключи и подпись
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
            user.setIdentityKey(identityKeyPubRaw);
            user.setSignedPreKey(signedPreKeyPubRaw);
            user.setOneTimePreKeys(oneTimePreKeysJson.toString());
            user.setSignedPreKeySignature(signedPreKeySignature);
            user.setPublicKey(identityKeyPubRaw);
            userRepository.save(user);
            Map<String, String> bundle = new HashMap<>();
            bundle.put("identityKey", identityKeyPubRaw);
            bundle.put("signedPreKey", signedPreKeyPubRaw);
            bundle.put("oneTimePreKeys", oneTimePreKeysJson.toString());
            bundle.put("signedPreKeySignature", signedPreKeySignature);
            bundle.put("publicKey", identityKeyPubRaw);
            return bundle;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка генерации ключей X3DH", e);
        }
    }
}
