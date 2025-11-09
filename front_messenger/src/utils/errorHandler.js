// Утилита для обработки ошибок авторизации и API запросов
class ErrorHandler {
    static lastRedirectTime = 0;
    static redirectCooldown = 5000; // 5 секунд между редиректами

    static handleAuthError(error, context = 'API') {
        console.error(`${context} Error:`, error);

        // Если это ошибка 401 или 403 - токен недействительный
        if (error.response?.status === 401 || error.response?.status === 403) {
            console.log('Unauthorized access detected');

            // Проверяем, не относится ли запрос к API друзей
            const isFriendsEndpoint = error.config?.url?.includes('/friends/');

            // НЕ очищаем данные авторизации для запросов API друзей,
            // так как проблема может быть в формате запроса, а не в токене
            if (!isFriendsEndpoint) {
                const token = localStorage.getItem('token');
                const user = localStorage.getItem('user');

                if (token || user) {
                    console.log('Clearing invalid auth data due to 401/403 response');
                    localStorage.removeItem('token');
                    localStorage.removeItem('user');

                    // Диспатчим событие об изменении авторизации
                    window.dispatchEvent(new Event('authChange'));
                }
            } else {
                console.log('Сохраняем токен для запросов API друзей, несмотря на ошибку 403');
            }

            // Проверяем, не был ли недавно редирект
            const now = Date.now();
            if (now - this.lastRedirectTime < this.redirectCooldown) {
                console.log('Redirect cooldown active, skipping redirect');
                return true;
            }

            // Не перенаправляем на логин при ошибках в API друзей
            if (isFriendsEndpoint) {
                console.log('Skipping login redirect for friends API error');
                return true;
            }

            // Редиректим на логин только если не находимся там
            if (window.location.pathname !== '/login' && window.location.pathname !== '/register') {
                this.lastRedirectTime = now;
                console.log('Redirecting to login page');
                window.location.replace('/login');
            }

            return true; // Указывает что ошибка была обработана
        }

        return false; // Ошибка не была обработана
    }

    static getErrorMessage(error) {
        if (!error) return 'Неизвестная ошибка';

        if (error.response) {
            const status = error.response.status;
            const data = error.response.data;

            switch (status) {
                case 400:
                    return data?.message || data?.error || 'Неверный запрос';
                case 401:
                    return 'Не авторизован. Войдите в систему';
                case 403:
                    return 'Доступ запрещен';
                case 404:
                    return 'Ресурс не найден';
                case 408:
                    return 'Время ожидания истекло';
                case 409:
                    return 'Конфликт данных';
                case 422:
                    return data?.message || 'Неверные данные';
                case 429:
                    return 'Слишком много запросов. Попробуйте позже';
                case 500:
                    return 'Внутренняя ошибка сервера';
                case 502:
                    return 'Сервер недоступен';
                case 503:
                    return 'Сервис временно недоступен';
                case 504:
                    return 'Время ожидания сервера истекло';
                default:
                    return data?.message || data?.error || `Ошибка сервера (${status})`;
            }
        }

        if (error.code === 'NETWORK_ERROR' || error.message?.includes('Network')) {
            return 'Ошибка сети. Проверьте подключение к интернету';
        }

        if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
            return 'Время ожидания истекло. Попробуйте еще раз';
        }

        return error.message || 'Неизвестная ошибка';
    }

    static isRetryableError(error) {
        if (!error.response) return true; // Network errors are retryable

        const status = error.response.status;
        return status >= 500 || status === 408 || status === 429;
    }
}

// Утилита для предотвращения дублирования запросов
class RequestManager {
    constructor() {
        this.pendingRequests = new Map();
    }

    // Создает ключ для запроса на основе URL и данных
    createRequestKey(url, data = null) {
        const dataStr = data ? JSON.stringify(data) : '';
        return `${url}_${dataStr}`;
    }

    // Выполняет запрос с защитой от дублирования
    async executeRequest(key, requestFunction) {
        // Если запрос уже выполняется, возвращаем существующий Promise
        if (this.pendingRequests.has(key)) {
            console.log(`Request ${key} already pending, returning existing promise`);
            return this.pendingRequests.get(key);
        }

        // Создаем новый Promise для запроса
        const requestPromise = requestFunction()
            .finally(() => {
                // Удаляем запрос из pending после завершения
                this.pendingRequests.delete(key);
            });

        // Сохраняем Promise в pending
        this.pendingRequests.set(key, requestPromise);

        return requestPromise;
    }

    // Отменяет все pending запросы
    cancelAllRequests() {
        this.pendingRequests.clear();
    }

    // Проверяет, выполняется ли запрос
    isRequestPending(key) {
        return this.pendingRequests.has(key);
    }
}

// Глобальный экземпляр менеджера запросов
const globalRequestManager = new RequestManager();

export { ErrorHandler, RequestManager, globalRequestManager };
