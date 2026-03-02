/**
 * keyBackupService.js
 *
 * Высокоуровневый сервис для управления зашифрованным бекапом E2EE-ключей.
 *
 * Схема работы:
 *  - Регистрация:  генерация ключей → шифрование KEK-паролем → сохранение blob на сервер
 *  - Вход (новое устройство):
 *      1. Загрузить blob с сервера
 *      2. Расшифровать KEK-паролем → восстановить ключи в localStorage
 *      3. Отправить актуальные публичные ключи на сервер (X3DH bundle)
 *  - Изменение ключей (при ротации):
 *      Повторно зашифровать и загрузить blob на сервер
 */

import api from './api';
import {
  encryptKeysWithPassword,
  decryptKeysWithPassword,
  collectLocalE2EEKeys,
  restoreLocalE2EEKeys,
  hasLocalE2EEKeys,
} from '../utils/keyEncryption';

const keyBackupService = {
  /**
   * Загрузить зашифрованный бекап ключей на сервер.
   * Вызывать после генерации/изменения ключей.
   *
   * @param {string} kekPassword - пароль шифрования ключей (не покидает браузер)
   * @returns {Promise<void>}
   */
  async uploadKeyBackup(kekPassword) {
    if (!kekPassword) throw new Error('KEK-пароль не указан');

    const keysJson = collectLocalE2EEKeys();
    if (!keysJson || keysJson === '{}') {
      throw new Error('Нет локальных E2EE-ключей для сохранения. Откройте любой чат и повторите.');
    }

    const encryptedPayload = await encryptKeysWithPassword(keysJson, kekPassword);

    await api.post('/api/users/me/key-backup', { encryptedPayload });
    console.log('[KeyBackup] ✓ Зашифрованный бекап ключей загружен на сервер');
  },

  /**
   * Скачать зашифрованный бекап ключей с сервера и восстановить в localStorage.
   *
   * @param {string} kekPassword - пароль шифрования ключей
   * @returns {Promise<boolean>} true — ключи восстановлены, false — бекап не найден
   * @throws {Error} если пароль неверный
   */
  async downloadAndRestoreKeys(kekPassword) {
    if (!kekPassword) throw new Error('KEK-пароль не указан');

    let response;
    try {
      response = await api.get('/api/users/me/key-backup');
    } catch (err) {
      if (err.response?.status === 404) {
        console.log('[KeyBackup] Бекап ключей не найден на сервере');
        return false;
      }
      throw err;
    }

    const { encryptedPayload } = response.data;
    if (!encryptedPayload) return false;

    // Расшифровка — бросит ошибку если пароль неверный
    const keysJson = await decryptKeysWithPassword(encryptedPayload, kekPassword);
    restoreLocalE2EEKeys(keysJson);
    console.log('[KeyBackup] ✓ Ключи успешно восстановлены из серверного бекапа');

    // Очищаем все session keys и fingerprints — пересчитаются при следующем открытии чата
    // с уже актуальными локальными ключами
    for (const key of Object.keys(localStorage)) {
      if (key.startsWith('e2ee_session_') || key.startsWith('e2ee_partner_fingerprint_')) {
        localStorage.removeItem(key);
      }
    }
    console.log('[KeyBackup] Session keys and fingerprints cleared — will re-derive on next chat open');
    // ВАЖНО: НЕ вызываем _republishPublicKeys здесь!
    // performX3DHHandshake в EnhancedChatWindow автоматически исправит рассинхрон
    // при следующем X3DH handshake без падения.
    return true;
  },

  /**
   * Перепубликует публичные X3DH ключи из localStorage на сервер.
   * Вызывается после восстановления ключей из бекапа.
   * @private
   */
  async _republishPublicKeys() {
    try {
      const userData = JSON.parse(localStorage.getItem('user') || '{}');
      const userId = userData?.id;
      if (!userId) return;

      const savedKeysJson = localStorage.getItem(`x3dh_keys_${userId}`);
      if (!savedKeysJson) return;

      const savedKeys = JSON.parse(savedKeysJson);

      // Поддержка обоих форматов хранения ключей
      // Формат 1: { identityPublic, identityPrivate, signedPrePublic, signedPrePrivate }
      // Формат 2: { identityKeyPair: { publicKey, privateKey }, signedPreKeyPair: { publicKey, privateKey } }
      let identityPublicB64 = null;
      let signedPrePublicB64 = null;

      if (savedKeys.identityPublic) {
        // Формат 1
        identityPublicB64 = savedKeys.identityPublic;
        signedPrePublicB64 = savedKeys.signedPrePublic;
      } else if (savedKeys.identityKeyPair) {
        // Формат 2 (JWK)
        // Экспортируем публичные ключи в raw Base64
        const idPubKey = await window.crypto.subtle.importKey(
          'jwk', savedKeys.identityKeyPair.publicKey,
          { name: 'ECDH', namedCurve: 'P-256' }, true, []
        );
        const signedPubKey = await window.crypto.subtle.importKey(
          'jwk', savedKeys.signedPreKeyPair.publicKey,
          { name: 'ECDH', namedCurve: 'P-256' }, true, []
        );
        const idRaw = await window.crypto.subtle.exportKey('raw', idPubKey);
        const signedRaw = await window.crypto.subtle.exportKey('raw', signedPubKey);
        identityPublicB64 = btoa(String.fromCharCode(...new Uint8Array(idRaw)));
        signedPrePublicB64 = btoa(String.fromCharCode(...new Uint8Array(signedRaw)));
      }

      if (!identityPublicB64 || !signedPrePublicB64) return;

      // signedPreKeySignature опциональна при восстановлении из бекапа
      const signatureB64 = '';

      // Генерируем заглушку для one-time prekeys (они хранятся в бекапе, но для простоты используем пустой массив)
      const oneTimePreKeys = JSON.stringify([]);

      await api.post(`/api/users/${userId}/prekey-bundle`, {
        identityKey: identityPublicB64,
        signedPreKey: signedPrePublicB64,
        oneTimePreKeys,
        signedPreKeySignature: signatureB64,
      });

      console.log('[KeyBackup] ✓ Публичные X3DH ключи перепубликованы на сервере');
    } catch (err) {
      console.warn('[KeyBackup] Не удалось перепубликовать публичные ключи:', err.message);
      // Не бросаем ошибку — ключи всё равно восстановлены
    }
  },

  /**
   * Проверить, существует ли бекап ключей на сервере.
   * @returns {Promise<boolean>}
   */
  async hasServerBackup() {
    try {
      const response = await api.get('/api/users/me/key-backup');
      return !!response.data?.encryptedPayload;
    } catch (err) {
      if (err.response?.status === 404) return false;
      throw err;
    }
  },

  /**
   * Проверить, есть ли ключи локально.
   * @returns {boolean}
   */
  hasLocalKeys() {
    return hasLocalE2EEKeys();
  },
};

export default keyBackupService;
