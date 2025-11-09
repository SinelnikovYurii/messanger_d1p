import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import LoginForm from './components/LoginForm';
import RegisterForm from './components/RegisterForm';
import EnhancedChatPage from './pages/EnhancedChatPage';
import FriendsPage from './pages/FriendsPage';
import authService from './services/authService';
import TokenCleaner from './utils/tokenCleaner';
import { setAuthFromService } from './store/slices/authSlice';


function App() {
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const dispatch = useDispatch();

    useEffect(() => {
        // КРИТИЧНО: Очищаем устаревшие токены при запуске приложения
        const clearStaleTokens = () => {
            const token = localStorage.getItem('token');
            if (token) {
                // Проверяем, не истек ли токен локально
                if (authService.isTokenExpired(token)) {
                    console.log('App: Clearing expired token on startup');
                    authService.clearAuth();
                    return false;
                }
            }
            return true;
        };

        // Проверяем при загрузке приложения
        const initAuth = async () => {
            // КРИТИЧНО: Сначала выполняем автоматическую очистку устаревших токенов
            const wasCleared = TokenCleaner.autoCleanupIfNeeded();

            if (wasCleared) {
                console.log('App: Old tokens were automatically cleared');
                setIsAuthenticated(false);
                setIsLoading(false);
                return;
            }

            // Очищаем устаревшие токены
            const hasValidToken = clearStaleTokens();

            if (!hasValidToken) {
                setIsAuthenticated(false);
                setIsLoading(false);
                return;
            }

            // ИСПРАВЛЕНИЕ: Используем только локальную проверку при загрузке
            // Не делаем валидацию на сервере сразу - это уменьшит нагрузку
            const hasValidLocalAuth = authService.hasToken() && authService.getUserData();

            if (hasValidLocalAuth) {
                // Если есть локальные данные, сразу устанавливаем авторизацию
                setIsAuthenticated(true);
                // ИСПРАВЛЕНИЕ: Синхронизируем Redux store при загрузке
                dispatch(setAuthFromService());
                console.log('App: Local auth valid, user authenticated, Redux synchronized');
            } else {
                setIsAuthenticated(false);
                console.log('App: No valid local auth');
            }

            setIsLoading(false);
        };

        initAuth();

        // Подписываемся на изменения авторизации через AuthService
        const unsubscribe = authService.addListener((authenticated) => {
            console.log('App: Auth state changed via AuthService:', authenticated);
            setIsAuthenticated(authenticated);
            // ИСПРАВЛЕНИЕ: Синхронизируем Redux при изменении авторизации
            if (authenticated) {
                dispatch(setAuthFromService());
                console.log('App: Redux store synchronized after auth change');
            }
        });

        // Слушаем изменения в localStorage (например, при логине в другой вкладке)
        const handleStorageChange = (e) => {
            if (e.key === 'token' || e.key === 'user') {
                console.log('App: Storage changed, rechecking auth status');
                const hasValidLocalAuth = authService.hasToken() && authService.getUserData();
                setIsAuthenticated(hasValidLocalAuth);
                // ИСПРАВЛЕНИЕ: Синхронизируем Redux при изменении storage
                if (hasValidLocalAuth) {
                    dispatch(setAuthFromService());
                }
            }
        };

        window.addEventListener('storage', handleStorageChange);

        // ИСПРАВЛЕНИЕ: Упрощенный обработчик authChange без повторной валидации
        const handleAuthChange = () => {
            console.log('App: Auth change event received');
            const hasValidLocalAuth = authService.hasToken() && authService.getUserData();
            setIsAuthenticated(hasValidLocalAuth);
            // ИСПРАВЛЕНИЕ: Синхронизируем Redux
            if (hasValidLocalAuth) {
                dispatch(setAuthFromService());
            }
        };

        window.addEventListener('authChange', handleAuthChange);

        return () => {
            unsubscribe();
            window.removeEventListener('storage', handleStorageChange);
            window.removeEventListener('authChange', handleAuthChange);
        };
    }, [dispatch]);

    // Показываем загрузку пока проверяем аутентификацию
    if (isLoading) {
        return (
            <div className="flex items-center justify-center min-h-screen" data-app-loading="true">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
                    <p className="text-gray-600">Загрузка...</p>
                </div>
            </div>
        );
    }

    return (
        <Router
            future={{
                v7_startTransition: true,
                v7_relativeSplatPath: true,
            }}
        >
            <Routes>
                <Route path="/" element={<Navigate to={isAuthenticated ? '/chat' : '/login'} replace />} />
                <Route path="/login" element={isAuthenticated ? <Navigate to="/chat" replace /> : <LoginForm setIsAuthenticated={setIsAuthenticated} />} />
                <Route path="/register" element={isAuthenticated ? <Navigate to="/chat" replace /> : <RegisterForm setIsAuthenticated={setIsAuthenticated} />} />
                <Route path="/chat" element={isAuthenticated ? <EnhancedChatPage /> : <Navigate to="/login" replace />} />
                <Route path="/friends" element={isAuthenticated ? <FriendsPage /> : <Navigate to="/login" replace />} />
            </Routes>
        </Router>
    );
}

export default App;