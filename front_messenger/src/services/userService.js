import api from './api';

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
    const response = await api.post('/api/users/friends/request', { userId });
    return response.data;
  },

  respondToFriendRequest: async (requestId, accept) => {
    console.log('userService: Отправка ответа на запрос дружбы:', { requestId, accept });
    try {
      // Создаем объект запроса с необходимыми полями
      const payload = {
        requestId: requestId,
        accept: accept    // Добавляем поле accept, которое ожидает бэкенд
      };

      // Логируем точный payload
      console.log('userService: Payload для отправки:', JSON.stringify(payload));

      // Логируем конфигурацию запроса перед отправкой
      console.log('userService: URL запроса:', '/api/users/friends/respond');
      console.log('userService: Метод запроса:', 'POST');

      // Отправляем запрос
      const response = await api.post('/api/users/friends/respond', payload);

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
    const response = await api.get('/api/users/friends/incoming');
    return response.data;
  },

  getOutgoingFriendRequests: async () => {
    const response = await api.get('/api/users/friends/outgoing');
    return response.data;
  },

  removeFriend: async (friendId) => {
    const response = await api.delete(`/api/users/friends/${friendId}`);
    return response.data;
  },

  // Обновить статус онлайн
  updateOnlineStatus: async (isOnline) => {
    const response = await api.post(`/api/users/status/online?isOnline=${isOnline}`);
    return response.data;
  },

  // Получить всех пользователей (для совместимости)
  getAllUsers: async () => {
    const response = await api.get('/api/users/all');
    return response.data;
  }
};

export default userService;
