import { validateToken } from './api';
import { ErrorHandler, globalRequestManager } from '../utils/errorHandler';

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
