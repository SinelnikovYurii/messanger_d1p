/**
 * keyEncryption.js
 *
 * Модуль для шифрования приватных E2EE-ключей паролем пользователя.
 *
 * Алгоритм:
 *  1. Из пароля шифрования ключей (KEK-password) + случайной соли
 *     выводим ключ шифрования ключей (KEK) через PBKDF2-SHA-256, 310_000 итераций, 256 бит.
 *  2. KEK-ом шифруем JSON-дамп приватных ключей через AES-256-GCM.
 *  3. На сервер отправляем { salt, iv, ciphertext } — всё в Base64.
 *  4. Сервер хранит только зашифрованный blob и не может его расшифровать.
 *
 * Безопасность:
 *  - KEK-password никогда не покидает браузер.
 *  - Соль уникальна для каждого сохранения.
 *  - PBKDF2 с 310_000 итерациями соответствует рекомендации OWASP 2023 для SHA-256.
 */

const PBKDF2_ITERATIONS = 310_000;
const KEY_LENGTH_BITS = 256;
const SALT_LENGTH = 32;   // 256-bit salt
const IV_LENGTH = 12;     // 96-bit IV для AES-GCM

// ──────────────────────────────────────────────────────────────────────────────
// Вспомогательные функции
// ──────────────────────────────────────────────────────────────────────────────

function toBase64(buffer) {
  return btoa(String.fromCharCode(...new Uint8Array(buffer)));
}

function fromBase64(b64) {
  return Uint8Array.from(atob(b64), c => c.charCodeAt(0));
}

// ──────────────────────────────────────────────────────────────────────────────
// Вывод KEK из пароля
// ──────────────────────────────────────────────────────────────────────────────

async function deriveKEK(password, salt) {
  const enc = new TextEncoder();
  const keyMaterial = await window.crypto.subtle.importKey(
    'raw',
    enc.encode(password),
    { name: 'PBKDF2' },
    false,
    ['deriveKey']
  );

  return window.crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt,
      iterations: PBKDF2_ITERATIONS,
      hash: 'SHA-256',
    },
    keyMaterial,
    { name: 'AES-GCM', length: KEY_LENGTH_BITS },
    false,
    ['encrypt', 'decrypt']
  );
}

// ──────────────────────────────────────────────────────────────────────────────
// Публичный API
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Шифрует JSON-дамп ключей паролем (KEK-password).
 *
 * @param {string} keysJson  - JSON-строка с приватными ключами
 * @param {string} kekPassword - пароль шифрования ключей
 * @returns {Promise<string>} - JSON-строка { salt, iv, ciphertext } в Base64
 */
export async function encryptKeysWithPassword(keysJson, kekPassword) {
  const salt = window.crypto.getRandomValues(new Uint8Array(SALT_LENGTH));
  const iv   = window.crypto.getRandomValues(new Uint8Array(IV_LENGTH));

  const kek = await deriveKEK(kekPassword, salt);

  const encoded = new TextEncoder().encode(keysJson);
  const ciphertext = await window.crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    kek,
    encoded
  );

  return JSON.stringify({
    salt:       toBase64(salt),
    iv:         toBase64(iv),
    ciphertext: toBase64(ciphertext),
  });
}

/**
 * Расшифровывает зашифрованный blob ключей паролем (KEK-password).
 *
 * @param {string} encryptedPayload - JSON-строка { salt, iv, ciphertext }
 * @param {string} kekPassword      - пароль шифрования ключей
 * @returns {Promise<string>} - JSON-строка с приватными ключами
 * @throws {Error} если пароль неверный или данные повреждены
 */
export async function decryptKeysWithPassword(encryptedPayload, kekPassword) {
  let parsed;
  try {
    parsed = JSON.parse(encryptedPayload);
  } catch {
    throw new Error('Некорректный формат зашифрованного бекапа ключей');
  }

  const salt       = fromBase64(parsed.salt);
  const iv         = fromBase64(parsed.iv);
  const ciphertext = fromBase64(parsed.ciphertext);

  const kek = await deriveKEK(kekPassword, salt);

  let decrypted;
  try {
    decrypted = await window.crypto.subtle.decrypt(
      { name: 'AES-GCM', iv },
      kek,
      ciphertext
    );
  } catch {
    throw new Error('Неверный пароль шифрования ключей или данные повреждены');
  }

  return new TextDecoder().decode(decrypted);
}

/**
 * Собирает JSON-дамп всех локальных E2EE-ключей из localStorage.
 * Включает:
 *  - e2ee_privateKey        (основной приватный ключ, JWK)
 *  - x3dh_keys_*            (X3DH identity + prekey pairs, JWK)
 *  - e2ee_session_*         (session keys, Base64-AES)
 *
 * @returns {string} JSON-строка
 */
export function collectLocalE2EEKeys() {
  const keys = {};

  for (const storageKey of Object.keys(localStorage)) {
    if (
      storageKey === 'e2ee_privateKey' ||
      storageKey.startsWith('x3dh_keys_') ||
      storageKey.startsWith('e2ee_session_')
      // e2ee_partner_fingerprint_ намеренно НЕ включаем — они пересчитываются автоматически
    ) {
      const value = localStorage.getItem(storageKey);
      if (value) keys[storageKey] = value;
    }
  }

  return JSON.stringify(keys);
}

/**
 * Восстанавливает E2EE-ключи из JSON-дампа в localStorage.
 *
 * @param {string} keysJson - JSON-строка (результат collectLocalE2EEKeys)
 */
export function restoreLocalE2EEKeys(keysJson) {
  let parsed;
  try {
    parsed = JSON.parse(keysJson);
  } catch {
    throw new Error('Некорректный формат дампа ключей');
  }

  for (const [storageKey, value] of Object.entries(parsed)) {
    if (
      storageKey === 'e2ee_privateKey' ||
      storageKey.startsWith('x3dh_keys_') ||
      storageKey.startsWith('e2ee_session_')
    ) {
      localStorage.setItem(storageKey, value);
    }
  }
}

/**
 * Проверяет, есть ли хоть какой-то E2EE ключ в localStorage.
 * @returns {boolean}
 */
export function hasLocalE2EEKeys() {
  for (const key of Object.keys(localStorage)) {
    if (key === 'e2ee_privateKey' || key.startsWith('x3dh_keys_')) {
      return true;
    }
  }
  return false;
}
