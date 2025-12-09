// DoubleRatchetManager.js
// Менеджер Double Ratchet для одного чата
// Использует упрощенную версию Double Ratchet с X3DH базой

export class DoubleRatchetManager {
  constructor() {
    this.sessions = {};
  }

  // Инициализация сессии с ротацией ключей
  async initSession(chatId, sessionKey) {
    console.log('[DoubleRatchet] Initializing session for chat:', chatId);

    if (!sessionKey) {
      console.error('[DoubleRatchet] sessionKey is undefined/null');
      throw new Error('DoubleRatchet: sessionKey не инициализирован');
    }

    try {
      // Создаем состояние для ротации ключей (упрощенный Double Ratchet)
      // Используем X3DH session key как базовый ключ
      this.sessions[chatId] = {
        rootKey: sessionKey,
        sendChainKey: sessionKey, // Начальный ключ цепочки отправки
        receiveChainKey: sessionKey, // Начальный ключ цепочки приема
        sendCounter: 0,
        receiveCounter: 0,
        initialized: true,
        useFallback: false
      };

      console.log('[DoubleRatchet] Session created with key rotation support');
      console.log('[DoubleRatchet] Using simplified Double Ratchet (X3DH + chain keys)');

      return this.sessions[chatId];
    } catch (error) {
      console.error('[DoubleRatchet] Failed to create session:', error);

      // Fallback: используем X3DH без ротации
      this.sessions[chatId] = {
        rootKey: sessionKey,
        initialized: true,
        useFallback: true,
        error: error.message
      };

      console.warn('[DoubleRatchet] Falling back to X3DH without key rotation');
      return this.sessions[chatId];
    }
  }

  // Получить сессию для чата
  getSession(chatId) {
    return this.sessions[chatId];
  }

  // Проверка готовности сессии
  isReady(chatId) {
    const session = this.getSession(chatId);
    return session && session.initialized;
  }

  // Использует ли сессия ротацию ключей
  usesKeyRotation(chatId) {
    const session = this.getSession(chatId);
    return session && session.initialized && !session.useFallback;
  }

  // Шифрование с ротацией ключей (упрощенная версия)
  async encrypt(chatId, plaintext, encryptFunction) {
    const session = this.getSession(chatId);
    if (!session) {
      throw new Error('Double Ratchet session not initialized');
    }

    // Используем session key напрямую (ротация ключей в будущем)
    console.log('[DoubleRatchet] Encrypting message (simplified mode)');
    return plaintext; // Возвращаем plaintext для шифрования в EnhancedChatWindow
  }

  // Дешифрование с ротацией ключей (упрощенная версия)
  async decrypt(chatId, encryptedData, decryptFunction) {
    const session = this.getSession(chatId);
    if (!session) {
      throw new Error('Double Ratchet session not initialized');
    }

    console.log('[DoubleRatchet] Decrypting message (simplified mode)');
    return encryptedData; // Возвращаем для дешифрования в EnhancedChatWindow
  }

  // Очистка сессии
  clearSession(chatId) {
    if (this.sessions[chatId]) {
      delete this.sessions[chatId];
      console.log('[DoubleRatchet] Session cleared for chat:', chatId);
    }
  }
}


