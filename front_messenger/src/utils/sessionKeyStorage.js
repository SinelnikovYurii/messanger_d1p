// sessionKeyStorage.js
// Хранилище для session keys в localStorage

const SESSION_KEY_PREFIX = 'e2ee_session_';

/**
 * Экспорт CryptoKey в формат для хранения
 */
async function exportSessionKey(cryptoKey) {
  const exported = await window.crypto.subtle.exportKey('raw', cryptoKey);
  return btoa(String.fromCharCode(...new Uint8Array(exported)));
}

/**
 * Импорт CryptoKey из хранилища
 */
async function importSessionKey(base64Key) {
  const binaryString = atob(base64Key);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }

  return await window.crypto.subtle.importKey(
    'raw',
    bytes,
    { name: 'AES-GCM' },
    true,
    ['encrypt', 'decrypt']
  );
}

/**
 * Генерация ключа для хранения (детерминированный по обоим userId)
 */
function getStorageKey(userId1, userId2) {
  // Убедимся, что сортируем корректно как строки (работает для чисел и UUID)
  const ids = [String(userId1), String(userId2)].sort((a, b) => a.localeCompare(b));
  const [id1, id2] = ids;
  return `${SESSION_KEY_PREFIX}${id1}_${id2}`;
}

/**
 * Сохранить session key для чата
 */
export async function saveSessionKey(myUserId, partnerId, sessionKey) {
  try {
    const storageKey = getStorageKey(myUserId, partnerId);
    const exportedKey = await exportSessionKey(sessionKey);
    localStorage.setItem(storageKey, exportedKey);
    console.log('[SessionKeyStorage] Session key saved for users:', myUserId, partnerId);
    return true;
  } catch (error) {
    console.error('[SessionKeyStorage] Failed to save session key:', error);
    return false;
  }
}

/**
 * Получить session key для чата
 */
export async function loadSessionKey(myUserId, partnerId) {
  try {
    const storageKey = getStorageKey(myUserId, partnerId);
    const exportedKey = localStorage.getItem(storageKey);

    if (!exportedKey) {
      console.log('[SessionKeyStorage] No saved session key found for users:', myUserId, partnerId);
      return null;
    }

    const sessionKey = await importSessionKey(exportedKey);
    console.log('[SessionKeyStorage] Session key loaded for users:', myUserId, partnerId);
    return sessionKey;
  } catch (error) {
    console.error('[SessionKeyStorage] Failed to load session key:', error);
    return null;
  }
}

/**
 * Удалить session key для чата (например, при выходе или сбросе)
 */
export function removeSessionKey(myUserId, partnerId) {
  try {
    const storageKey = getStorageKey(myUserId, partnerId);
    localStorage.removeItem(storageKey);
    console.log('[SessionKeyStorage] Session key removed for users:', myUserId, partnerId);
    return true;
  } catch (error) {
    console.error('[SessionKeyStorage] Failed to remove session key:', error);
    return false;
  }
}

/**
 * Очистить все session keys (например, при выходе)
 */
export function clearAllSessionKeys() {
  try {
    const keys = Object.keys(localStorage);
    let count = 0;

    keys.forEach(key => {
      if (key.startsWith(SESSION_KEY_PREFIX)) {
        localStorage.removeItem(key);
        count++;
      }
    });

    console.log('[SessionKeyStorage] Cleared', count, 'session keys');
    return true;
  } catch (error) {
    console.error('[SessionKeyStorage] Failed to clear session keys:', error);
    return false;
  }
}

/**
 * Очистить все E2EE ключи (session keys + X3DH keys) для полного пересоздания
 * Используется при обнаружении несовпадения ключей
 */
export function clearAllE2EEKeys(myUserId) {
  try {
    console.log('[SessionKeyStorage] Clearing ALL E2EE keys for full resync...');

    const keys = Object.keys(localStorage);
    let sessionKeysCleared = 0;
    let x3dhKeysCleared = 0;

    keys.forEach(key => {
      // Очищаем session keys
      if (key.startsWith(SESSION_KEY_PREFIX)) {
        localStorage.removeItem(key);
        sessionKeysCleared++;
      }

      // Очищаем X3DH keys
      if (key.startsWith('x3dh_keys_')) {
        localStorage.removeItem(key);
        x3dhKeysCleared++;
      }
    });

    console.log('[SessionKeyStorage] ✓ Cleared:', {
      sessionKeys: sessionKeysCleared,
      x3dhKeys: x3dhKeysCleared
    });

    return true;
  } catch (error) {
    console.error('[SessionKeyStorage] Failed to clear all E2EE keys:', error);
    return false;
  }
}
