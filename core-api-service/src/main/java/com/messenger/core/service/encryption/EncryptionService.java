package com.messenger.core.service.encryption;

import com.messenger.core.service.user.UserService;

import java.util.Map;

/**
 * Контракт для управления криптографическими операциями E2EE.
 * Отвечает за хранение, получение и генерацию публичных ключей,
 * prekey bundle (X3DH) и зашифрованных бекапов ключей.
 * <p>
 * Согласно SRP, этот интерфейс отделяет криптографическую ответственность
 * от бизнес-логики управления пользователями ({@link UserService}).
 */
public interface EncryptionService {

    /**
     * Сохранить публичный ключ пользователя.
     *
     * @param userId    ID пользователя
     * @param publicKey публичный ключ в формате Base64
     */
    void savePublicKey(Long userId, String publicKey);

    /**
     * Получить публичный ключ пользователя.
     *
     * @param userId ID пользователя
     * @return публичный ключ или null, если не найден
     */
    String getPublicKey(Long userId);

    /**
     * Сохранить X3DH prekey bundle пользователя.
     *
     * @param userId               ID пользователя
     * @param identityKey          идентификационный ключ
     * @param signedPreKey         подписанный предварительный ключ
     * @param oneTimePreKeys       одноразовые предварительные ключи (JSON-массив)
     * @param signedPreKeySignature подпись подписанного ключа
     */
    void savePreKeyBundle(Long userId, String identityKey, String signedPreKey,
                          String oneTimePreKeys, String signedPreKeySignature);

    /**
     * Получить X3DH prekey bundle пользователя.
     * Если ключи отсутствуют — автоматически генерирует новые.
     *
     * @param userId ID пользователя
     * @return map с полями identityKey, signedPreKey, oneTimePreKeys, publicKey
     */
    Map<String, String> getPreKeyBundle(Long userId);

    /**
     * Получить X3DH prekey bundle пользователя в бинарном виде (JSON → byte[]).
     *
     * @param userId ID пользователя
     * @return байтовый массив JSON-сериализованного bundle
     */
    byte[] getPreKeyBundleBinary(Long userId);

    /**
     * Получить PreKeyBundleProtocol для Double Ratchet.
     * Возвращает расширенный bundle с разобранным списком oneTimePreKeys.
     *
     * @param userId ID пользователя
     * @return map с полями userId, identityKey, signedPreKey, signedPreKeySignature,
     *         oneTimePreKeys (List), publicKey
     */
    Map<String, Object> getPreKeyBundleProtocol(Long userId);

    /**
     * Сгенерировать и сохранить новый X3DH prekey bundle для пользователя.
     * Всегда создаёт новые ключи, не использует кешированные.
     *
     * @param userId ID пользователя
     * @return map с новыми ключами
     */
    Map<String, String> generateAndSavePreKeyBundle(Long userId);

    /**
     * Сохранить зашифрованный бекап E2EE ключей пользователя.
     * Сервер не знает пароль шифрования и не может расшифровать данные.
     *
     * @param userId           ID пользователя
     * @param encryptedPayload зашифрованные данные в формате Base64/JSON
     */
    void saveEncryptedKeyBackup(Long userId, String encryptedPayload);

    /**
     * Получить зашифрованный бекап E2EE ключей пользователя.
     *
     * @param userId ID пользователя
     * @return зашифрованный payload или null, если бекап не найден
     */
    String getEncryptedKeyBackup(Long userId);
}
