import api from './api';
import {
  generateKeyPair,
  exportPublicKey,
  importPublicKey,
  deriveSessionKey,
  encryptMessage,
  decryptMessage
} from '../utils/crypto';

const userService = {
  // Поиск пользователей
  searchUsers: async (query) => {
    const response = await api.get(`/api/users/search?query=${encodeURIComponent(query)}`);
    return response.data;
  },

  // Получить информацию о пользователе
  getUserInfo: async (userId) => {
    const response = await api.get(`/api/users/${userId}`);
    return response.data;
  },

  // Обновить профиль
  updateProfile: async (profileData) => {
    const response = await api.put('/api/users/profile', profileData);
    return response.data;
  },

  // Загрузить аватар
  uploadAvatar: async (file) => {
    const formData = new FormData();
    formData.append('file', file);

    const response = await api.post('/api/users/profile/avatar', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },

  // Управление друзьями
  sendFriendRequest: async (userId) => {
    const response = await api.post('/api/friends/request', { userId });
    return response.data;
  },

  respondToFriendRequest: async (requestId, accept) => {
    console.log('userService: Отправка ответа на запрос дружбы:', { requestId, accept });
    try {
      const payload = {
        requestId: requestId,
        accept: accept
      };
      console.log('userService: Payload для отправки:', JSON.stringify(payload));
      console.log('userService: URL запроса:', '/api/friends/respond');
      console.log('userService: Метод запроса:', 'POST');
      const response = await api.post('/api/friends/respond', payload);
      console.log('userService: Успешный ответ от сервера:', response.data);
      return response.data;
    } catch (error) {
      console.error('userService: Ошибка при ответе на запрос дружбы:', error);

      // Расширенное логирование ошибки
      if (error.request) {
        console.error('userService: Детали отправленного запроса:', error.request);
      }

      if (error.response) {
        console.error('userService: Статус ошибки:', error.response.status);
        console.error('userService: Заголовки ответа:', error.response.headers);
        console.error('userService: Данные ошибки:', error.response.data);
      }

      // Проверка, не связана ли ошибка с авторизацией
      if (error.response && (error.response.status === 401 || error.response.status === 403)) {
        console.warn('userService: Возможная проблема с авторизацией - проверьте JWT токен');
      }

      throw error;
    }
  },

  getFriends: async () => {
    const response = await api.get('/api/users/friends');
    return response.data;
  },

  getIncomingFriendRequests: async () => {
    const response = await api.get('/api/friends/incoming');
    return response.data;
  },

  getOutgoingFriendRequests: async () => {
    const response = await api.get('/api/friends/outgoing');
    return response.data;
  },

  removeFriend: async (friendId) => {
    const response = await api.delete(`/api/friends/${friendId}`);
    return response.data;
  },

  // Обновить статус онлайн
  updateOnlineStatus: async (isOnline) => {
    const response = await api.post(`/api/users/status/online?isOnline=${isOnline}`);
    return response.data;
  },

  // Получить всех пользователей (для совместимости)
  getAllUsers: async () => {
    const currentUser = JSON.parse(localStorage.getItem('user'));
    const userId = currentUser?.id;
    const response = await api.get('/api/users/all', {
      headers: {
        ...(userId ? { 'x-user-id': userId } : {})
      }
    });
    return response.data;
  },

  // Сохранение публичного ключа пользователя на сервере
  saveUserPublicKey: async (userId, publicKey) => {
    return api.post(`/api/users/${userId}/public-key`, { publicKey });
  },

  // Получение публичного ключа другого пользователя
  getUserPublicKey: async (userId) => {
    const response = await api.get(`/api/users/${userId}/public-key`);
    return response.data.publicKey;
  },

  // Сохранение X3DH prekey bundle пользователя на сервере
  savePreKeyBundle: async (userId, identityKey, signedPreKey, oneTimePreKeys, signedPreKeySignature) => {
    return api.post(`/api/users/${userId}/prekey-bundle`, { identityKey, signedPreKey, oneTimePreKeys, signedPreKeySignature });
  },

  // Получение X3DH prekey bundle другого пользователя
  getPreKeyBundle: async (userId) => {
    const response = await api.get(`/api/users/${userId}/prekey-bundle`);
    return response.data; // JSON object
  },

  // Получение PreKeyBundleProtocol для Double Ratchet
  getPreKeyBundleProtocol: async (userId) => {
    const response = await api.get(`/api/users/${userId}/prekey-bundle-protocol`);
    return response.data; // JSON object
  },
};

export default userService;
