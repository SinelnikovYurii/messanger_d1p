import axios from 'axios';
import { ErrorHandler } from '../utils/errorHandler';

// URL конфигурация
export const CHAT_SERVER_URL = 'ws://localhost:8083/ws';
export const CHAT_SERVER_URL_DIRECT = 'ws://localhost:8080/ws';

// Создаем основной API клиент через Gateway
const api = axios.create({
  baseURL: 'http://localhost:8083', // Gateway порт
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Добавляем JWT токен к каждому запросу
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Обработка ответов с улучшенной обработкой ошибок
api.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('API Error:', error);

    // Используем централизованный обработчик ошибок
    const handled = ErrorHandler.handleAuthError(error, 'Main API');

    // Если ошибка не была обработана как авторизационная, просто пробрасываем её
    return Promise.reject(error);
  }
);

// Специфичные API клиенты для разных сервисов
export const authApi = axios.create({
  baseURL: 'http://localhost:8081', // Authorization Service
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const chatApi = axios.create({
  baseURL: 'http://localhost:8083', // Изменяем с 8082 на 8083 (Gateway)
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const userApi = axios.create({
  baseURL: 'http://localhost:8083', // Изменяем с 8082 на 8083 (Gateway)
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const messageApi = axios.create({
  baseURL: 'http://localhost:8083', // Изменяем с 8082 на 8083 (Gateway)
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const gatewayApi = axios.create({
  baseURL: 'http://localhost:8083', // Gateway
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Добавляем токены ко всем API клиентам кроме authApi
[chatApi, userApi, messageApi, gatewayApi].forEach(apiClient => {
  apiClient.interceptors.request.use(
    (config) => {
      const token = localStorage.getItem('token');
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    },
    (error) => Promise.reject(error)
  );

  // УБИРАЕМ автоматический редирект из этих клиентов
  apiClient.interceptors.response.use(
    (response) => response,
    (error) => {
      // Просто возвращаем ошибку без автоматического редиректа
      // Основной api клиент обработает 401 централизованно
      return Promise.reject(error);
    }
  );
});

// authApi не должен иметь перехватчик 401, так как он используется для логина
// Но добавим перехватчик запросов для совместимости (хотя токен обычно не нужен для логина)
authApi.interceptors.request.use(
  (config) => {
    // Для authApi обычно токен не нужен, но добавим для совместимости
    const token = localStorage.getItem('token');
    if (token && config.url !== '/auth/login' && config.url !== '/auth/register') {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Вспомогательные функции для аутентификации
export const login = async (credentials) => {
  const response = await authApi.post('/auth/login', credentials);
  return response.data;
};

export const register = async (userData) => {
  const response = await authApi.post('/auth/register', userData);
  return response.data;
};

export const validateToken = async (token) => {
  try {
    // Метод 1: Пробуем GET запрос с токеном в заголовке
    const response = await authApi.get('/auth/validate', {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    return response.data;
  } catch (error) {
    console.log('Method 1 (GET /auth/validate) failed:', error.response?.status);

    try {
      // Метод 2: Пробуем POST запрос (оригинальный метод)
      const response = await authApi.post('/auth/validate', { token });
      return response.data;
    } catch (error2) {
      console.log('Method 2 (POST /auth/validate) failed:', error2.response?.status);

      try {
        // Метод 3: Пробуем получить профиль пользователя как способ валидации
        const response = await authApi.get('/auth/profile', {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        });
        // Если профиль получен, токен валиден
        return { valid: true, user: response.data };
      } catch (error3) {
        console.log('Method 3 (GET /auth/profile) failed:', error3.response?.status);

        try {
          // Метод 4: Пробуем любой защищенный эндпоинт через основной API
          const response = await api.get('/api/users/me', {
            headers: {
              'Authorization': `Bearer ${token}`
            }
          });
          return { valid: true, user: response.data };
        } catch (error4) {
          console.log('Method 4 (GET /api/users/me) failed:', error4.response?.status);

          // Все методы валидации провалились
          console.error('All token validation methods failed');
          throw new Error(`Token validation failed: ${error4.response?.status || error4.message}`);
        }
      }
    }
  }
};

// Экспортируем основной API клиент как default
export default api;
