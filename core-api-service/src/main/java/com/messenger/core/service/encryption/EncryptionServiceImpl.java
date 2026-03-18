package com.messenger.core.service.encryption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.core.model.User;
import com.messenger.core.repository.UserRepository;
import com.messenger.core.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.*;

/**
 * Реализация {@link EncryptionService} — управление криптографическими данными E2EE пользователей.
 * <p>
 * Отвечает исключительно за сохранение/получение публичных ключей,
 * prekey bundle (X3DH) и зашифрованных бекапов ключей.
 * Взаимодействует с репозиторием напрямую, не через {@link UserService},
 * чтобы избежать циклических зависимостей.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class EncryptionServiceImpl implements EncryptionService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────
    // Публичный ключ
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void savePublicKey(Long userId, String publicKey) {
        User user = findUser(userId);
        user.setPublicKey(publicKey);
        userRepository.save(user);
        log.info("[ENC] Public key saved for user {}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public String getPublicKey(Long userId) {
        return findUser(userId).getPublicKey();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PreKey Bundle (X3DH)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void savePreKeyBundle(Long userId, String identityKey, String signedPreKey,
                                 String oneTimePreKeys, String signedPreKeySignature) {
        if (identityKey == null || identityKey.isEmpty()
                || signedPreKey == null || signedPreKey.isEmpty()
                || oneTimePreKeys == null || oneTimePreKeys.isEmpty()) {
            throw new IllegalArgumentException("identityKey, signedPreKey и oneTimePreKeys обязательны");
        }
        log.info("[ENC] Saving prekey bundle for user {}", userId);
        User user = findUser(userId);
        user.setIdentityKey(identityKey);
        user.setSignedPreKey(signedPreKey);
        user.setOneTimePreKeys(oneTimePreKeys);
        user.setSignedPreKeySignature(signedPreKeySignature != null ? signedPreKeySignature : "");
        user.setPublicKey(identityKey);
        userRepository.save(user);
        log.info("[ENC] Prekey bundle saved for user {}", userId);
    }

    @Override
    public Map<String, String> getPreKeyBundle(Long userId) {
        log.info("[ENC] Getting prekey bundle for user {}", userId);
        User user = findUser(userId);

        boolean needGen = isNullOrEmpty(user.getIdentityKey())
                || isNullOrEmpty(user.getSignedPreKey())
                || isNullOrEmpty(user.getOneTimePreKeys());

        if (needGen) {
            log.info("[ENC] Keys missing for user {}, auto-generating...", userId);
            Map<String, String> generated = generateAndSaveForUser(user);
            log.info("[ENC] Auto-generated keys for user {}", userId);
            return generated;
        }

        log.info("[ENC] Returning existing keys for user {}", userId);
        return buildStringBundle(user);
    }

    @Override
    public byte[] getPreKeyBundleBinary(Long userId) {
        Map<String, String> bundle = getPreKeyBundle(userId);
        if (bundle == null || bundle.isEmpty()) {
            return new byte[0];
        }
        try {
            return objectMapper.writeValueAsBytes(bundle);
        } catch (Exception e) {
            log.error("[ENC] Failed to serialize prekey bundle for user {}", userId, e);
            return new byte[0];
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getPreKeyBundleProtocol(Long userId) {
        User user = findUser(userId);
        Map<String, Object> bundle = new HashMap<>();
        bundle.put("userId", user.getId());
        bundle.put("identityKey", user.getIdentityKey());
        bundle.put("signedPreKey", user.getSignedPreKey());
        bundle.put("signedPreKeySignature", user.getSignedPreKeySignature());
        bundle.put("publicKey", user.getPublicKey());

        try {
            @SuppressWarnings("unchecked")
            List<String> oneTimePreKeysList =
                    objectMapper.readValue(user.getOneTimePreKeys(), List.class);
            bundle.put("oneTimePreKeys", oneTimePreKeysList);
        } catch (Exception e) {
            log.warn("[ENC] Failed to parse oneTimePreKeys for user {}", userId, e);
            bundle.put("oneTimePreKeys", Collections.emptyList());
        }
        return bundle;
    }

    @Override
    public Map<String, String> generateAndSavePreKeyBundle(Long userId) {
        log.info("[ENC] Force-generating new prekey bundle for user {}", userId);
        User user = findUser(userId);
        return generateAndSaveForUser(user);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Key Backup
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void saveEncryptedKeyBackup(Long userId, String encryptedPayload) {
        if (isNullOrEmpty(encryptedPayload)) {
            throw new IllegalArgumentException("encryptedPayload не может быть пустым");
        }
        User user = findUser(userId);
        user.setEncryptedKeyBackup(encryptedPayload);
        userRepository.save(user);
        log.info("[ENC] Encrypted key backup saved for user {}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public String getEncryptedKeyBackup(Long userId) {
        return findUser(userId).getEncryptedKeyBackup();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Приватные вспомогательные методы
    // ─────────────────────────────────────────────────────────────────────────

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + userId));
    }

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private Map<String, String> buildStringBundle(User user) {
        Map<String, String> bundle = new HashMap<>();
        bundle.put("identityKey", user.getIdentityKey());
        bundle.put("signedPreKey", user.getSignedPreKey());
        bundle.put("oneTimePreKeys", user.getOneTimePreKeys());
        bundle.put("publicKey", user.getPublicKey());
        return bundle;
    }

    /**
     * Генерирует EC-ключи (secp256r1), сохраняет их в переданного user и возвращает bundle.
     */
    private Map<String, String> generateAndSaveForUser(User user) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));

            // Identity key
            KeyPair identityKeyPair = kpg.generateKeyPair();
            String identityKeyPub = ecPublicKeyToBase64(identityKeyPair.getPublic());

            // Signed prekey
            KeyPair signedPreKeyPair = kpg.generateKeyPair();
            String signedPreKeyPub = ecPublicKeyToBase64(signedPreKeyPair.getPublic());

            // One-time prekeys
            StringBuilder otpkJson = new StringBuilder("[");
            for (int i = 0; i < 5; i++) {
                KeyPair otpk = kpg.generateKeyPair();
                otpkJson.append('"').append(ecPublicKeyToBase64(otpk.getPublic())).append('"');
                if (i < 4) otpkJson.append(',');
            }
            otpkJson.append("]");

            // Signature
            Signature ecdsaSign = Signature.getInstance("SHA256withECDSA");
            ecdsaSign.initSign(identityKeyPair.getPrivate());
            ecdsaSign.update(signedPreKeyPair.getPublic().getEncoded());
            String signature = Base64.getEncoder().encodeToString(ecdsaSign.sign());

            // Persist
            user.setIdentityKey(identityKeyPub);
            user.setSignedPreKey(signedPreKeyPub);
            user.setOneTimePreKeys(otpkJson.toString());
            user.setSignedPreKeySignature(signature);
            user.setPublicKey(identityKeyPub);
            userRepository.save(user);

            Map<String, String> bundle = new HashMap<>();
            bundle.put("identityKey", identityKeyPub);
            bundle.put("signedPreKey", signedPreKeyPub);
            bundle.put("oneTimePreKeys", otpkJson.toString());
            bundle.put("signedPreKeySignature", signature);
            bundle.put("publicKey", identityKeyPub);
            return bundle;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка генерации ключей X3DH", e);
        }
    }

    /**
     * Конвертирует публичный EC-ключ в Base64-строку uncompressed-формата (0x04 || x || y).
     */
    private static String ecPublicKeyToBase64(PublicKey publicKey) {
        java.security.interfaces.ECPublicKey ecPub = (java.security.interfaces.ECPublicKey) publicKey;
        java.security.spec.ECPoint w = ecPub.getW();

        byte[] x = padTo32(w.getAffineX().toByteArray());
        byte[] y = padTo32(w.getAffineY().toByteArray());

        byte[] uncompressed = new byte[65];
        uncompressed[0] = 0x04;
        System.arraycopy(x, 0, uncompressed, 1, 32);
        System.arraycopy(y, 0, uncompressed, 33, 32);
        return Base64.getEncoder().encodeToString(uncompressed);
    }

    private static byte[] padTo32(byte[] src) {
        if (src.length > 32) {
            return Arrays.copyOfRange(src, src.length - 32, src.length);
        }
        if (src.length < 32) {
            byte[] tmp = new byte[32];
            System.arraycopy(src, 0, tmp, 32 - src.length, src.length);
            return tmp;
        }
        return src;
    }
}
