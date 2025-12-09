import { validateToken } from './api';
import { ErrorHandler, globalRequestManager } from '../utils/errorHandler';
import userService from './userService';
import api from './api';
import {
  generateKeyPair,
  exportPublicKey,
  importPublicKey,
  deriveSessionKey,
  encryptMessage,
  decryptMessage,
  generateX3DHKeys,
  exportX3DHBundle,
  exportX3DHBundleWithSignature
} from '../utils/crypto';

// X3DH Handshake для установления сессионного ключа с собеседником
export async function performX3DHHandshake(myKeys, partnerId, myUserId) {
  console.log('[X3DH] Starting handshake with partner:', partnerId, 'myUserId:', myUserId);
  console.log('[X3DH] My identity key available:', !!myKeys.identityKeyPair);

  // КРИТИЧНО: Всегда получаем СВЕЖИЕ ключи партнера с сервера (не кэшированные!)
  const bundle = await userService.getPreKeyBundle(partnerId);
  console.log('[X3DH] Received FRESH bundle from server for partner:', partnerId, {
    hasIdentityKey: !!bundle.identityKey,
    hasSignedPreKey: !!bundle.signedPreKey,
    hasOneTimePreKeys: !!bundle.oneTimePreKeys,
    identityKeyLength: bundle.identityKey?.length,
    signedPreKeyLength: bundle.signedPreKey?.length,
  });

  // Проверяем наличие ключей
  if (!bundle.identityKey || !bundle.signedPreKey || !bundle.oneTimePreKeys) {
    throw new Error('Incomplete prekey bundle received');
  }

  console.log('[X3DH] Importing partner identityKey from raw format');
  const partnerIdentityKey = await importPublicKey(bundle.identityKey);
  console.log('[X3DH] Partner identity key imported successfully');

  // КРИТИЧНО: Также получаем СВОИ ключи с сервера для проверки синхронизации
  const myServerBundle = await userService.getPreKeyBundle(myUserId);
  const myPublicExportRaw = await window.crypto.subtle.exportKey('raw', myKeys.identityKeyPair.publicKey);
  const myPublicBase64 = btoa(String.fromCharCode(...new Uint8Array(myPublicExportRaw)));

  if (myPublicBase64 !== myServerBundle.identityKey) {
    console.error('[X3DH] CRITICAL: My local identity key does NOT match server!');
    console.error('[X3DH] Local key:', myPublicBase64.substring(0, 20) + '...');
    console.error('[X3DH] Server key:', myServerBundle.identityKey.substring(0, 20) + '...');
    throw new Error('Local keys are out of sync with server! Please reload the page.');
  } else {
    console.log('[X3DH] My local identity key matches server');
  }

  // Экспортируем ключи для детального логирования
  try {
    const myPublicBytes = new Uint8Array(myPublicExportRaw);
    const myPublicPreview = Array.from(myPublicBytes.slice(0, 10)).map(b => b.toString(16).padStart(2, '0')).join('');
    console.log('[X3DH] My public key preview:', myPublicPreview + '...');
    console.log('[X3DH] My public key (base64):', myPublicBase64.substring(0, 30) + '...');

    const partnerPublicExport = await window.crypto.subtle.exportKey('raw', partnerIdentityKey);
    const partnerPublicBytes = new Uint8Array(partnerPublicExport);
    const partnerPublicPreview = Array.from(partnerPublicBytes.slice(0, 10)).map(b => b.toString(16).padStart(2, '0')).join('');
    console.log('[X3DH] Partner public key preview:', partnerPublicPreview + '...');
    console.log('[X3DH] Partner public key (base64):', bundle.identityKey.substring(0, 30) + '...');
  } catch (e) {
    console.log('[X3DH] Could not export keys for preview');
  }

  console.log('[X3DH] Performing simplified ECDH...');

  // УПРОЩЁННАЯ ВЕРСИЯ: используем только identity keys для симметричного ключа
  // Оба пользователя получат одинаковый результат при DH(myPrivate, partnerPublic)
  const sharedSecret = await window.crypto.subtle.deriveBits(
    { name: 'ECDH', public: partnerIdentityKey },
    myKeys.identityKeyPair.privateKey,
    256
  );

  console.log('[X3DH] Shared secret derived');

  // Логируем shared secret (первые 10 байт)
  const sharedSecretBytes = new Uint8Array(sharedSecret);
  const sharedSecretPreview = Array.from(sharedSecretBytes.slice(0, 10)).map(b => b.toString(16).padStart(2, '0')).join('');
  console.log('[X3DH] Shared secret preview:', sharedSecretPreview + '...');

  // Добавляем контекстную информацию для уникальности ключа сессии
  // Используем отсортированные ID для детерминированности (строковая сортировка для UUID и чисел)
  const ids = [String(myUserId), String(partnerId)].sort((a, b) => a.localeCompare(b));
  const [id1, id2] = ids;
  const contextInfo = new TextEncoder().encode(`e2ee-session-${id1}-${id2}`);
  console.log('[X3DH] Context info:', `e2ee-session-${id1}-${id2}`);

  // Комбинируем shared secret с контекстом
  const combined = new Uint8Array([...new Uint8Array(sharedSecret), ...contextInfo]);
  const sessionKeyRaw = await window.crypto.subtle.digest('SHA-256', combined);

  console.log('[X3DH] Session key derived successfully (simplified)');

  // Логируем session key (первые 20 байт)
  const sessionKeyBytes = new Uint8Array(sessionKeyRaw);
  const sessionKeyPreview = Array.from(sessionKeyBytes.slice(0, 20)).map(b => b.toString(16).padStart(2, '0')).join('');
  console.log('[X3DH] Final session key preview:', sessionKeyPreview + '...');

  //extractable = true для возможности экспорта в Double Ratchet
  return await window.crypto.subtle.importKey('raw', sessionKeyRaw, { name: 'AES-GCM' }, true, ['encrypt', 'decrypt']);
}

class AuthService {
    constructor() {
        this.isValidating = false;
        this.validationPromise = null;
        this.listeners = [];
        this.requestManager = globalRequestManager;
    }

    // Добавить слушатель изменений авторизации
    addListener(callback) {
        this.listeners.push(callback);
        return () => {
            this.listeners = this.listeners.filter(listener => listener !== callback);
        };
    }

    // Уведомить всех слушателей об изменении
    notifyListeners(isAuthenticated) {
        this.listeners.forEach(callback => {
            try {
                callback(isAuthenticated);
            } catch (error) {
                console.error('Error in auth listener:', error);
            }
        });
    }

    // Проверка токена с защитой от множественных вызовов
    async validateAuthToken() {
        const token = localStorage.getItem('token');

        if (!token) {
            console.log('AuthService: No token found');
            this.clearAuth();
            return false;
        }

        const requestKey = `validate_token_${token.substring(0, 10)}`;

        try {
            return await this.requestManager.executeRequest(requestKey, () => this._performValidation());
        } catch (error) {
            console.error('AuthService: Validation failed:', error);
            ErrorHandler.handleAuthError(error, 'AuthService');
            return false;
        }
    }

    async _performValidation() {
        const token = localStorage.getItem('token');
        const user = localStorage.getItem('user');

        console.log('AuthService: Validating authentication');

        // Если нет базовых данных
        if (!token || !user || user === 'undefined' || user === 'null') {
            console.log('AuthService: No valid auth data found');
            this.clearAuth();
            return false;
        }

        try {
            // Парсим данные пользователя
            const parsedUser = JSON.parse(user);
            if (!parsedUser || typeof parsedUser !== 'object') {
                throw new Error('Invalid user data format');
            }

            // КРИТИЧНО: Валидируем токен на сервере
            try {
                console.log('AuthService: Validating token on server...');
                await validateToken(token);
                console.log('AuthService: Server token validation successful');
                this.notifyListeners(true);
                return true;

            } catch (error) {
                console.error('AuthService: Server validation failed:', error);

                // Если получили 401 или 403 - токен недействительный, очищаем данные
                if (error.response && (error.response.status === 401 || error.response.status === 403)) {
                    console.log('AuthService: Token is invalid (401/403), clearing auth data');
                    this.clearAuth();
                    return false;
                }

                // Если получили 500 или другие ошибки сервера - используем fallback
                console.warn('AuthService: Server error, using fallback validation');

                // Fallback: Проверяем токен локально
                if (this.isTokenExpired(token)) {
                    console.log('AuthService: Token expired locally, clearing auth data');
                    this.clearAuth();
                    return false;
                }

                console.log('AuthService: Fallback token validation successful');
                this.notifyListeners(true);
                return true;
            }

        } catch (error) {
            console.error('AuthService: Error parsing user data or validating token:', error);
            this.clearAuth();
            return false;
        }
    }

    // Очистка данных авторизации
    clearAuth() {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        this.notifyListeners(false);
        console.log('AuthService: Auth data cleared');

        // Отменяем все pending запросы при очистке авторизации
        this.requestManager.cancelAllRequests();
    }

    // Сохранение данных авторизации
    setAuth(token, userData) {
        if (!token || !userData) {
            throw new Error('Invalid auth data provided');
        }

        localStorage.setItem('token', token);
        localStorage.setItem('user', JSON.stringify(userData));

        // Немедленно уведомляем слушателей об успешной авторизации
        this.notifyListeners(true);
        console.log('AuthService: Auth data saved and listeners notified');

        // Диспатчим событие для App.jsx
        window.dispatchEvent(new Event('authChange'));
    }

    // Проверка наличия токена (без валидации)
    hasToken() {
        const token = localStorage.getItem('token');
        return token && token !== 'undefined' && token !== 'null' && token.length > 10;
    }

    // Получение данных пользователя
    getUserData() {
        try {
            const user = localStorage.getItem('user');
            console.log('AuthService: getUserData called, raw data:', user);
            if (user && user !== 'undefined' && user !== 'null') {
                const parsedUser = JSON.parse(user);
                console.log('AuthService: Parsed user data:', parsedUser);
                if (parsedUser && typeof parsedUser === 'object') {
                    return parsedUser;
                }
            }
        } catch (error) {
            console.error('AuthService: Error parsing user data:', error);
            this.clearAuth();
        }
        console.log('AuthService: getUserData returning null');
        return null;
    }

    // Безопасный логаут
    logout() {
        this.clearAuth();

        // Используем replace вместо href для лучшего UX
        if (window.location.pathname !== '/login') {
            window.location.replace('/login');
        }
    }

    // Проверка готовности к API запросам
    isReadyForApiCalls() {
        return this.hasToken() && this.getUserData() !== null;
    }

    // Локальная проверка истечения JWT токена
    isTokenExpired(token) {
        if (!token) return true;

        try {
            // Декодируем JWT токен (только payload, без проверки подписи)
            const payload = JSON.parse(atob(token.split('.')[1]));

            // Проверяем время истечения (exp в секундах)
            if (payload.exp) {
                const currentTime = Math.floor(Date.now() / 1000);
                const isExpired = currentTime > payload.exp;

                if (isExpired) {
                    console.log('AuthService: Token expired locally', {
                        currentTime,
                        expTime: payload.exp,
                        expired: isExpired
                    });
                }

                return isExpired;
            }

            // Если нет поля exp, считаем токен действительным
            console.log('AuthService: Token has no expiration field, treating as valid');
            return false;

        } catch (error) {
            console.error('AuthService: Error parsing token for expiration check:', error);
            // Если не удается распарсить токен, считаем его недействительным
            return true;
        }
    }
}

// Создаем единственный экземпляр
const authService = new AuthService();

export default authService;

// Добавить методы для отправки публичного ключа на сервер при регистрации/логине
export async function registerUserWithKey(userData) {
  const keyPair = await generateKeyPair();
  const publicKey = await exportPublicKey(keyPair.publicKey);
  // Сохраняем приватный ключ в secure storage (реализовать отдельно)
  // Отправляем публичный ключ вместе с userData
  return api.post('/register', { ...userData, publicKey });
}

export async function loginUserWithKey(userData) {
  const keyPair = await generateKeyPair();
  const publicKey = await exportPublicKey(keyPair.publicKey);
  // Сохраняем приватный ключ в secure storage (реализовать отдельно)
  // Отправляем публичный ключ вместе с userData
  return api.post('/login', { ...userData, publicKey });
}

// Регистрация с генерацией и отправкой публичного ключа
export async function registerUserWithE2EE(userData) {
  const keyPair = await generateKeyPair();
  const publicKey = await exportPublicKey(keyPair.publicKey);
  // Сохраняем приватный ключ в localStorage (или IndexedDB, рекомендуется шифровать)
  localStorage.setItem('e2ee_privateKey', JSON.stringify(await window.crypto.subtle.exportKey('jwk', keyPair.privateKey)));
  // Отправляем публичный ключ на сервер
  const response = await userService.saveUserPublicKey(userData.id, publicKey);
  // Продолжаем обычную регистрацию
  // ...отправка userData на сервер...
  return response;
}

// Получение публичного ключа другого пользователя для E2EE
export async function getChatPartnerPublicKey(partnerId) {
  return await userService.getUserPublicKey(partnerId);
}

// Генерация и загрузка X3DH ключей при регистрации
export async function registerUserWithX3DH(userData) {
  // Генерируем X3DH ключи
  const x3dhKeys = await generateX3DHKeys();
  // Экспортируем bundle для сервера с подписью
  const bundle = await exportX3DHBundleWithSignature(x3dhKeys);
  // Сохраняем bundle на сервере
  await userService.savePreKeyBundle(userData.id, bundle.identityKey, bundle.signedPreKey, bundle.oneTimePreKeys, bundle.signedPreKeySignature);
  // ...дальнейшая регистрация пользователя...
  return true;
}

// Полная регистрация пользователя с E2EE и Double Ratchet
export async function registerUserWithE2EEAndX3DH(userData) {
  // 1. Генерация пары ключей для E2EE (совместимость)
  const keyPair = await generateKeyPair();
  const publicKey = await exportPublicKey(keyPair.publicKey);
  localStorage.setItem('e2ee_privateKey', JSON.stringify(await window.crypto.subtle.exportKey('jwk', keyPair.privateKey)));
  await userService.saveUserPublicKey(userData.id, publicKey);

  // 2. Генерация и загрузка X3DH ключей с подписью для Double Ratchet
  const x3dhKeys = await generateX3DHKeys();
  const bundle = await exportX3DHBundleWithSignature(x3dhKeys);
  await userService.savePreKeyBundle(userData.id, bundle.identityKey, bundle.signedPreKey, bundle.oneTimePreKeys, bundle.signedPreKeySignature);

  // 3. Продолжаем обычную регистрацию пользователя (отправка userData)
  // Если нужно, отправьте userData на сервер
  const response = await api.post('/register', userData);
  // Можно сохранить токен и данные пользователя, если они пришли в ответ
  if (response.data && response.data.token && response.data.user) {
    authService.setAuth(response.data.token, response.data.user);
  }
  return response;
}
