// Утилита для очистки устаревших JWT токенов после изменения секретного ключа
// Этот скрипт должен быть запущен один раз после изменения JWT секрета на всех сервисах

class TokenCleaner {
    static clearAllTokens() {
        console.log('🧹 TokenCleaner: Clearing all stored authentication data');

        // Очищаем все связанные с авторизацией данные из localStorage
        const keysToRemove = ['token', 'user', 'authTimestamp', 'refreshToken'];

        keysToRemove.forEach(key => {
            if (localStorage.getItem(key)) {
                console.log(`🗑️ Removing ${key} from localStorage`);
                localStorage.removeItem(key);
            }
        });

        // Очищаем sessionStorage на всякий случай
        keysToRemove.forEach(key => {
            if (sessionStorage.getItem(key)) {
                console.log(`🗑️ Removing ${key} from sessionStorage`);
                sessionStorage.removeItem(key);
            }
        });

        console.log('✅ TokenCleaner: All authentication data cleared');

        // Диспатчим событие об изменении авторизации
        window.dispatchEvent(new Event('authChange'));

        return true;
    }

    static forceLogoutAllUsers() {
        this.clearAllTokens();

        // Если не на странице логина, перенаправляем туда
        if (window.location.pathname !== '/login') {
            console.log('🔄 Redirecting to login page');
            window.location.replace('/login');
        }

        // Показываем уведомление пользователю
        if (window.alert) {
            window.alert('Система безопасности обновлена. Пожалуйста, войдите в систему заново.');
        }
    }

    // Проверяет, нужна ли очистка токенов (например, по версии)
    static needsTokenCleanup() {
        const lastCleanupVersion = localStorage.getItem('tokenCleanupVersion');
        const currentVersion = '2.0.0'; // Увеличиваем версию для принудительной очистки после изменения JWT секрета

        return !lastCleanupVersion || lastCleanupVersion !== currentVersion;
    }

    // Отмечает, что очистка токенов была выполнена
    static markTokenCleanupComplete() {
        const currentVersion = '2.0.0'; // Синхронизируем версию
        localStorage.setItem('tokenCleanupVersion', currentVersion);
        console.log('✅ Token cleanup marked as complete for version:', currentVersion);
    }

    // Автоматическая очистка при необходимости
    static autoCleanupIfNeeded() {
        if (this.needsTokenCleanup()) {
            console.log('🔄 Auto-cleanup needed, clearing old tokens');
            this.clearAllTokens();
            this.markTokenCleanupComplete();
            return true;
        }
        return false;
    }
}

export default TokenCleaner;
